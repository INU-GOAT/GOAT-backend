package com.capstone.goat.dto.response;

import com.capstone.goat.domain.Game;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class GameFinishedResponseDto {

    private final long gameId;

    private final String sportName;

    private final LocalDateTime startTime;

    private final String court;

    private final Integer result;

    @Builder(access = AccessLevel.PRIVATE)
    private GameFinishedResponseDto(long gameId, String sportName, LocalDateTime startTime, String court, Integer result) {
        this.gameId = gameId;
        this.sportName = sportName;
        this.startTime = startTime;
        this.court = court;
        this.result = result;
    }

    public static GameFinishedResponseDto of(Game game, Integer result) {

        return GameFinishedResponseDto.builder()
                .gameId(game.getId())
                .sportName(game.getSport().getName())
                .startTime(game.getStartTime())
                .court(game.getCourt())
                .result(result)
                .build();
    }
}