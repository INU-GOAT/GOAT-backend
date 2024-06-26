package com.capstone.goat.service;


import com.capstone.goat.domain.Club;
import com.capstone.goat.domain.User;
import com.capstone.goat.dto.request.ClubSaveDto;
import com.capstone.goat.dto.request.ClubUpdateDte;
import com.capstone.goat.dto.response.ApplicantListResponseDto;
import com.capstone.goat.dto.response.ClubListResponseDto;
import com.capstone.goat.dto.response.ClubMemberResponseDto;
import com.capstone.goat.dto.response.ClubResponseDto;
import com.capstone.goat.exception.ex.CustomErrorCode;
import com.capstone.goat.exception.ex.CustomException;
import com.capstone.goat.repository.ClubRepository;
import com.capstone.goat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubService {
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long createClub(User user, ClubSaveDto clubSaveDto){
        if(user.getClub()!=null){
            throw new CustomException(CustomErrorCode.HAS_CLUB);
        }
        if(clubRepository.existsByName(clubSaveDto.getName())){
            throw new CustomException(CustomErrorCode.DUPLICATE_CLUB_NAME);
        }
        Club club = clubRepository.save(Club.builder().name(clubSaveDto.getName()).master_id(user.getId()).sport(clubSaveDto.getSport()).build());
        User master = userRepository.findById(user.getId()).get();
        master.joinClub(club);
        return club.getId();
    }

    @Transactional
    public Long updateClub(Long userId, Long clubId, ClubUpdateDte clubUpdateDte){
        Club  club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        if(!club.getMaster_id().equals(userId)){
            throw new CustomException(CustomErrorCode.ONLY_MASTER_AUTH);
        }
        club.update(clubUpdateDte.getName(), clubUpdateDte.getSport());
        return club.getId();
    }

    @Transactional
    public void deleteClub(Long userId,Long clubId){
        Club  club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        if(!club.getMaster_id().equals(userId)){
            throw new CustomException(CustomErrorCode.ONLY_MASTER_AUTH);
        }
        clubRepository.delete(club);
    }



    public ClubResponseDto getClub (Long id){
        Club club = clubRepository.findById(id).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        List<ClubMemberResponseDto> members = club.getMembers().stream().map(ClubMemberResponseDto::of).toList();
        User master = userRepository.findById(club.getMaster_id()).orElseThrow(()-> new CustomException(CustomErrorCode.USER_NOT_FOUND));
        return ClubResponseDto.of(club,master.getNickname(),members);
    }
    @Transactional
    public Long applyClub(Long userId, Long clubId){
        User user = userRepository.findById(userId).orElseThrow(()-> new CustomException(CustomErrorCode.USER_NOT_FOUND));
        if(user.getApplyingClub()!=null||user.getClub()!=null){
            throw new CustomException(CustomErrorCode.APPLYING_CLUB_EXIST);
        }
        Club club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        if(club.getMembers().size()==20){
            throw new CustomException(CustomErrorCode.FULL_MEMBER);
        }
        user.applyClub(club);
        return userId;
    }
    public List<ApplicantListResponseDto> getApplicantList(Long clubId){
        Club club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        return club.getApplicants().stream().map(ApplicantListResponseDto::of).collect(Collectors.toList());
    }
    @Transactional
    public Long acceptClub(Long userId,Long applicantId, Long clubId, boolean accept){
        Club club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        if(!club.getMaster_id().equals(userId)){
            throw new CustomException(CustomErrorCode.ONLY_MASTER_AUTH);
        }
        List<Long> applicant = club.getApplicants().stream().map(User::getId).toList();
        if(!applicant.contains(applicantId)){
            throw new CustomException(CustomErrorCode.NOT_APPLY_USER);
        }
        User user = userRepository.findById(applicantId).orElseThrow(()-> new CustomException(CustomErrorCode.USER_NOT_FOUND));
        user.fineApply();
        if(accept) {
            log.info("[로그] 클럽 가입 승인 - applicant: {}, club: {}", user.getNickname(), club.getName());
            user.joinClub(club);
        }
        if(club.getMembers().size()==20){
            for(int i = 0 ; i < club.getApplicants().size();i++){
                deleteApplicant(club.getApplicants().get(i).getId());
            }
        }
        return userId;
    }

    @Transactional
    public Long kickClub(Long userId, Long memberId, Long clubId){
        Club club = clubRepository.findById(clubId).orElseThrow(()->new CustomException(CustomErrorCode.CLUB_NOT_FOUND));
        if(!club.getMaster_id().equals(userId)){
            throw new CustomException(CustomErrorCode.ONLY_MASTER_AUTH);
        }
        if(club.getMaster_id().equals(memberId)){
            throw new CustomException(CustomErrorCode.MASTER_NOT_OUT);
        }
        User user = userRepository.findById(memberId).orElseThrow(()-> new CustomException(CustomErrorCode.USER_NOT_FOUND));
        user.kickClub();
        return memberId;
    }

    @Transactional
    public void deleteApplicant(Long userId){
        User user = userRepository.findById(userId).orElseThrow(()-> new CustomException(CustomErrorCode.USER_NOT_FOUND));
        user.fineApply();
    }

    @Transactional(readOnly = true)
    public List<ClubListResponseDto> getClubList(){
        return clubRepository.findClubsWithLessThan20Members().stream().map(ClubListResponseDto::of).collect(Collectors.toList());
    }






}
