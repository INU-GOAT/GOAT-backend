package com.capstone.goat.service;

import com.capstone.goat.domain.*;
import com.capstone.goat.dto.request.MatchingConditionDto;
import com.capstone.goat.exception.ex.CustomErrorCode;
import com.capstone.goat.exception.ex.CustomException;
import com.capstone.goat.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchMakingService {

    private final MatchingRepository matchingRepository;
    private final MatchMakingRepository matchMakingRepository;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final GroupRepository groupRepository;
    private final GameRepository gameRepository;
    private final TeammateRepository teammateRepository;
    private final NotificationService notificationService;
    private final VotedCourtRepository votedCourtRepository;

    @Transactional
    public long addMatchingAndMatchMaking(MatchingConditionDto matchingConditionDto, long userId, int rating) {

        // 사용자에게 그룹이 없을 경우 생성
        Group group = groupService.getGroup(userId, false);

        int userCount = group.getMembers().size();
        log.info("[로그] 매칭 DB 추가, userCount = " + userCount);

        // 그룹장이 아닌 경우 매칭 시작 불가능
        if (!Objects.equals(group.getMasterId(), userId)) {
            throw new CustomException(CustomErrorCode.MATCHING_ACCESS_DENIED);
        }

        // 클럽 매칭의 경우 그룹원이 해당 스포츠 인원보다 적으면 매칭 불가능
        // 일반 매칭의 경우 그룹원이 해당 스프츠 인원보다 많으면 매칭 불가능
        String sportName = matchingConditionDto.getSport();
        Sport sport = Sport.getSport(sportName);
        log.info("[로그] 스포츠 이름: {}, 스포츠 인원 수: {}", sport.getName(), sport.getPlayer());
        if (matchingConditionDto.getIsClubMatching()) {
            if (userCount < sport.getPlayer()) {
                throw new CustomException(CustomErrorCode.NOT_ENOUGH_GROUP_MEMBERS);
            }
        } else {
            if (userCount > sport.getPlayer()) {
                throw new CustomException(CustomErrorCode.TOO_MANY_GROUP_MEMBERS);
            }
        }

        // 그룹원 모두 매칭 중으로 상태 변경, 매칭 대기 상태가 아니면 예외 발생
        group.getMembers().forEach(member -> {
            if (Status.WAITING == member.getStatus()) {
                member.changeStatus(Status.MATCHING);
            } else {
                throw new CustomException(CustomErrorCode.NOT_WAITING_STATE);
            }
        });

        // Matching Repository에 저장
        Matching matching = matchingConditionDto.toEntity(rating, group);
        List<MatchStartTime> matchStartTimeList = matchingConditionDto.getMatchStartTimes().stream()
                .map(stringStartTime ->
                        MatchStartTime.builder()
                                .startTime(stringStartTime)
                                .matching(matching)
                                .build()
                )
                .toList();  // Dto의 List<String> matchStartTimes를 List<MatchStartTime>으로 변환
        matching.addMatchStartTimes(matchStartTimeList);
        matchingRepository.save(matching);

        // MatchMaking Repository에 저장
        matchingConditionDto.toMatchMakingList(userCount, rating, group.getId())  // List<MatchMaking>
                .forEach(matchMakingRepository::save);

        return group.getId();
    }

    @Async
    @Transactional
    public void findMatching(MatchingConditionDto matchingConditionDto, long groupId, int rating, int matchingRange) {

        for (MatchMaking matchMaking : matchingConditionDto.toMatchMakingList(0, rating, groupId)) {

            log.info("[로그] 매치메이킹 시작, groupId = {}, isClubMatching = {}, matchingRange = {}", groupId, matchMaking.getIsClubMatching(), matchingRange);

            // 조건에 맞는 매칭 중인 유저 검색
            List<MatchMaking> matchMakingList = matchMakingRepository.findByMatchingAndMatchingRange(matchMaking, matchingRange);

            log.info("[로그] 조건에 맞는 매칭 중인 유저, matchMakingList = " + matchMakingList);

            // 검색한 리스트가 비어있으면 다음으로
            if (matchMakingList.size() < 2) continue;

            double latitude = matchingConditionDto.getLatitude();
            double longitude = matchingConditionDto.getLongitude();

            /*--클럽 매칭-----------------------------------------------------------------------------------*/
            if (matchingConditionDto.getIsClubMatching()) {
                MatchMaking club1 = matchMakingList.get(0);
                MatchMaking club2 = matchMakingList.get(1);

                List<Long> club1GroupId = new ArrayList<>();
                club1GroupId.add(club1.getGroupId());
                List<Long> club2GroupId = new ArrayList<>();
                club2GroupId.add(club2.getGroupId());
                Set<PreferCourt> preferCourtSet = new HashSet<>();
                preferCourtSet.add(
                        PreferCourt.builder()
                                .court(club1.getPreferCourt())
                                .latitude(club1.getLatitude())
                                .longitude(club1.getLongitude())
                                .build()
                );
                preferCourtSet.add(
                        PreferCourt.builder()
                                .court(club2.getPreferCourt())
                                .latitude(club2.getLatitude())
                                .longitude(club2.getLongitude())
                                .build()
                );
                List<PreferCourt> preferCourtList = new ArrayList<>(preferCourtSet);

                log.info("[로그] : club1GroupId: " + club1GroupId + " club2GroupId: " + club2GroupId);

                // Matching과 MatchMaking에서 매칭된 그룹 제거
                deleteMatchedClub(club1, club2);

                // Game에 추가
                Long gameId = addGame(club1GroupId, club2GroupId, matchMaking, preferCourtList);

                // 매칭된 모든 유저를 게임 중으로 상태 변경 및 매칭 완료 알림 전송
                initiateUserGaming(gameId);

                // 매칭된 그룹 모두 해체
                disbandGroupAll(club1GroupId, club2GroupId);

                return;
            }

            /*--일반 매칭--------------------------------------------------------------------------------*/
            // 스포츠 인원에 맞는 팀 구성이 되는지 확인
            int player = Sport.getSport(matchingConditionDto.getSport()).getPlayer();
            List<MatchMaking> team1 = findSumSubset(matchMakingList, player);
            if (team1.isEmpty()) continue;
            List<MatchMaking> team2 = findSumSubset(matchMakingList, player);

            if (!team2.isEmpty()) {
                List<Long> team1GroupId = team1.stream().map(MatchMaking::getGroupId).toList();
                List<Long> team2GroupId = team2.stream().map(MatchMaking::getGroupId).toList();
                List<PreferCourt> preferCourtList = getPerferCourtList(team1, team2);

                log.info("[로그] : team1GroupId: " + team1GroupId + " team2GroupId: " + team2GroupId);

                // Matching과 MatchMaking에서 매칭된 그룹 제거
                deleteMatchedGroup(team1, team2);

                // Game에 추가
                Long gameId = addGame(team1GroupId, team2GroupId, matchMaking, preferCourtList);

                // 매칭된 모든 유저를 게임 중으로 상태 변경 및 매칭 완료 알림 전송
                initiateUserGaming(gameId);

                // 매칭된 그룹 모두 해체
                disbandGroupAll(team1GroupId, team2GroupId);

                return;
            }
        }
    }

    // 그룹 인원 수의 합이 스포츠 한 팀의 수와 같은 집합 검색
    private List<MatchMaking> findSumSubset(List<MatchMaking> matchMakingList, int target) {

        int n = matchMakingList.size();
        boolean[][] dp = new boolean[n + 1][target + 1];

        // 모든 부분집합은 빈 집합을 포함하기 때문에 true로 초기화
        for (int i = 0; i <= n; i++) {
            dp[i][0] = true;
        }

        // 동적 프로그래밍을 사용하여 부분집합의 합을 계산
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= target; j++) {
                if (matchMakingList.get(i - 1).getUserCount() > j) {
                    dp[i][j] = dp[i - 1][j];
                } else {
                    dp[i][j] = dp[i - 1][j] || dp[i - 1][j - matchMakingList.get(i - 1).getUserCount()];
                }
            }
        }

        // 부분집합을 구성하는 요소를 찾아내기
        List<MatchMaking> subset = new ArrayList<>();
        if (dp[n][target]) {
            int i = n, j = target;
            while (i > 0 && j > 0) {
                if (!dp[i - 1][j]) {
                    subset.add(matchMakingList.get(i - 1));
                    j -= matchMakingList.get(i - 1).getUserCount();
                    matchMakingList.remove(i - 1);
                }
                i--;
            }
        }

        return subset;
    }

    // MatchMaking 리스트를 중복이 제거된 PreferCourt 리스트로 변환
    private List<PreferCourt> getPerferCourtList(List<MatchMaking> team1, List<MatchMaking> team2) {

        Set<PreferCourt> preferCourtSet = new HashSet<>();

        preferCourtSet.addAll(
            team1.stream().map(matchMaking ->
                    PreferCourt.builder()
                            .court(matchMaking.getPreferCourt())
                            .latitude(matchMaking.getLatitude())
                            .longitude(matchMaking.getLongitude())
                            .build()
            ).toList()
        );
        preferCourtSet.addAll(
                team2.stream().map(matchMaking ->
                        PreferCourt.builder()
                                .court(matchMaking.getPreferCourt())
                                .latitude(matchMaking.getLatitude())
                                .longitude(matchMaking.getLongitude())
                                .build()
                ).toList()
        );

        return new ArrayList<>(preferCourtSet);
    }

    // Matching과 MatchMaking에서 매칭된 클럽 제거
    private void deleteMatchedClub(MatchMaking club1, MatchMaking club2) {

        log.info("[로그] deleteByGroupId 시작");

        matchMakingRepository.deleteByGroupIdAndLatitudeAndLongitude(club1.getGroupId(), club1.getLatitude(), club1.getLongitude());
        matchingRepository.deleteByGroupId(club1.getGroupId());

        matchMakingRepository.deleteByGroupIdAndLatitudeAndLongitude(club2.getGroupId(), club2.getLatitude(), club2.getLongitude());
        matchingRepository.deleteByGroupId(club2.getGroupId());

    }

    // Matching과 MatchMaking에서 매칭된 그룹 제거
    private void deleteMatchedGroup(List<MatchMaking> team1, List<MatchMaking> team2) {

        log.info("[로그] deleteByGroupId 시작");

        for (MatchMaking matchMaking : team1) {
            matchMakingRepository.deleteByGroupIdAndLatitudeAndLongitude(matchMaking.getGroupId(), matchMaking.getLatitude(), matchMaking.getLongitude());
            matchingRepository.deleteByGroupId(matchMaking.getGroupId());
        }
        for (MatchMaking matchMaking : team2) {
            matchMakingRepository.deleteByGroupIdAndLatitudeAndLongitude(matchMaking.getGroupId(), matchMaking.getLatitude(), matchMaking.getLongitude());
            matchingRepository.deleteByGroupId(matchMaking.getGroupId());
        }
    }

    // 게임 생성
    private Long addGame(List<Long> team1, List<Long> team2, MatchMaking matchMaking, List<PreferCourt> preferCourtList) {

        log.info("[로그] addGame() 시작");

        LocalDateTime matchStartDateTime = getMatchStartDateTime(matchMaking.getMatchStartTime());

        ClubGame clubGame = initializeClubGame(matchMaking, team1, team2);

        Game game = createAndSaveGame(matchMaking, matchStartDateTime, clubGame);

        saveTeammates(team1, game, 1);
        saveTeammates(team2, game, 2);

        assignPreferCourts(preferCourtList, game);

        return game.getId();
    }

    private LocalDateTime getMatchStartDateTime(String matchStartTime) {

        log.info("[로그] getMatchStartDateTime() 시작");

        // 시작 시간 파싱
        LocalTime matchStartTimeParsed = LocalTime.parse(matchStartTime, DateTimeFormatter.ofPattern("HHmm"));
        // 현재 날짜와 시작 시간을 합쳐서 LocalDateTime 객체 생성
        return LocalDate.now().atTime(matchStartTimeParsed);
    }

    private ClubGame initializeClubGame(MatchMaking matchMaking, List<Long> team1, List<Long> team2) {

        log.info("[로그] initializeClubGame() 시작");

        if (matchMaking.getIsClubMatching()) {
            ClubGame clubGame = new ClubGame();
            groupRepository.findById(team1.get(0)).ifPresent(group ->
                    clubGame.appendTeam1Info(group.getMasterId(), group.getClubId())
            );
            groupRepository.findById(team2.get(0)).ifPresent(group ->
                    clubGame.appendTeam2Info(group.getMasterId(), group.getClubId())
            );
            return clubGame;
        }
        return null;
    }

    private Game createAndSaveGame(MatchMaking matchMaking, LocalDateTime matchStartDateTime, ClubGame clubGame) {

        log.info("[로그] createAndSaveGame() 시작");

        Game newGame = Game.builder()
                .sport(matchMaking.getSport())
                .startTime(matchStartDateTime)
                .clubGame(clubGame)
                .build();
        return gameRepository.save(newGame);
    }

    private void saveTeammates(List<Long> team, Game game, int teamNumber) {

        log.info("[로그] saveTeammates() 시작");

        team.forEach(groupId ->
                Optional.ofNullable(groupRepository.findUsersById(groupId))
                        .stream().flatMap(Collection::stream)
                        .forEach(user ->
                                teammateRepository.save(
                                        Teammate.builder()
                                                .teamNumber(teamNumber)
                                                .game(game)
                                                .userId(user.getId())
                                                .build()
                                )
                        )
        );
    }

    private void assignPreferCourts(List<PreferCourt> preferCourtList, Game game) {

        log.info("[로그] assignPreferCourts() 시작");

        preferCourtList.forEach(preferCourt -> {
            preferCourt.determineGame(game);
            game.addPreferCourt(preferCourt);
            votedCourtRepository.save(VotedCourt.builder().court(preferCourt.getCourt()).game(game).build());
        });

        if (game.getClubGame() != null) {
            PreferCourt court = preferCourtList.get(0);
            game.determineCourt(court.getCourt(), court.getLatitude(), court.getLongitude());
        }
    }

    private void initiateUserGaming(Long gameId) {

        log.info("[로그] initiateUserGaming() 시작 - gameId: {}", gameId);

        // 매칭된 모든 유저를 게임 중으로 상태 변경 및 매칭 완료 알림 전송
        teammateRepository.findUserIdsByGameId(gameId).forEach(userId -> {
            User user = getUser(userId);
            user.changeStatus(Status.GAMING);
            notificationService.sendNotification(null, user.getNickname(), NotificationType.MATCHING);
        });
    }

    // 매칭된 그룹 모두 삭제
    private void disbandGroupAll(List<Long> team1, List<Long> team2) {

        log.info("[로그] disbandGroupAll() 시작");

        for (long groupId: team1) {
            groupRepository.findById(groupId)
                    .ifPresent(groupService::disbandGroup);
        }

        for (Long groupId : team2) {
            groupRepository.findById(groupId)
                    .ifPresent(groupService::disbandGroup);
        }
    }

    @Transactional
    public void deleteMatching(long userId) {

        log.info("[로그] deleteMatching() 시작");

        User user = getUser(userId);
        Group group = Optional.ofNullable(user.getGroup())
                .orElseThrow(() -> new CustomException(CustomErrorCode.NO_JOINING_GROUP));

        // 그룹장이 아닌 경우 매칭 종료 불가능
        if (!Objects.equals(group.getMasterId(), user.getId()))
            throw new CustomException(CustomErrorCode.MATCHING_ACCESS_DENIED);

        // 매칭 삭제
        long groupId = group.getId();
        Matching foundMatching = matchingRepository.findByGroupId(groupId).
                orElseThrow(() -> new CustomException(CustomErrorCode.NO_MATCHING));
        matchMakingRepository.deleteByGroupIdAndLatitudeAndLongitude(groupId, foundMatching.getLatitude(), foundMatching.getLongitude());
        matchingRepository.deleteByGroupId(groupId);

        // 그룹원 모두 대기 중으로 상태 변경
        group.getMembers().forEach(member -> member.changeStatus(Status.WAITING));

        // 그룹 인원이 1명인 경우 그룹 삭제
        if(group.getMembers().size() == 1){
            groupService.disbandGroup(group);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.USER_NOT_FOUND));
    }

}
