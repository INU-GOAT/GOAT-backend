package com.capstone.goat.controller;

import com.capstone.goat.domain.User;
import com.capstone.goat.dto.request.LoginDto;
import com.capstone.goat.dto.request.TokenDto;
import com.capstone.goat.dto.response.ResponseDto;
import com.capstone.goat.dto.response.UserResponseDto;
import com.capstone.goat.dto.request.UserSaveDto;
import com.capstone.goat.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
        private final UserService userService;
    @Operation(summary = "회원가입", description = "바디에 {name,phone,loing_id,password,age,soccer_career,basketball_career,badminton_career,soccer_position,basketball_position}을 json형식으로 보내주세요. 경력이나 포지션을 입력하지 않았을경우 값에 -1을 담아 보내주세요")
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "회원가입성공",content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400",description = "잘못된 입력",content = @Content(schema = @Schema(implementation = ResponseDto.class)))
    })
    @PostMapping("")
    public ResponseEntity<ResponseDto<Long>> save(@Valid@RequestBody UserSaveDto userSaveDto){
        Long id = userService.save(userSaveDto);
        log.info("유저 save 호출 id={}",id);
        return new ResponseEntity<>(new ResponseDto<>(id,"회원가입성공"), HttpStatus.OK);
    }

    @Operation(summary = "유저 정보 가져오기", description = "url 파라미터에 유저의 데이터베이스 id 값을 보내주세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "회원가입성공",content = @Content(schema = @Schema(implementation = ResponseDto.class))),
            @ApiResponse(responseCode = "400",description = "잘못된 입력",content = @Content(schema = @Schema(implementation = ResponseDto.class)))
    })
    @GetMapping("")
    public ResponseEntity<ResponseDto<UserResponseDto>> getUser(@Schema(hidden = true)@AuthenticationPrincipal User user){
        return new ResponseEntity<>(new ResponseDto<>(userService.getUser(user),"회원가입성공"), HttpStatus.OK);
    }

    @Operation(summary= "로그인", description = "body에 {login_id, password 를 보내주세요.")
    @ApiResponses({
            @ApiResponse(responseCode = "200",description = "로그인 성공",content = @Content(schema = @Schema(implementation = TokenDto.class))),
            @ApiResponse(responseCode = "401",description = "아이디가 존재하지 않습니다. / 비밀번호가 틀립니다.",content = @Content(schema = @Schema(implementation = ResponseDto.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<ResponseDto<TokenDto>> getUser(@Valid @RequestBody LoginDto loginDto){
        return new ResponseEntity<>(new ResponseDto<>(userService.login(loginDto),"회원가입성공"), HttpStatus.OK);
    }



}
