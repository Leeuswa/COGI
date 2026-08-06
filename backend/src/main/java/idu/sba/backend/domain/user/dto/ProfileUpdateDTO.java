package idu.sba.backend.domain.user.dto;

import idu.sba.backend.domain.user.entity.Level;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class ProfileUpdateDTO {

    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자여야 합니다.")
    private String nickname;

    @NotNull(message = "수준을 선택해 주세요")
    private Level level;

    private List<String> interests;
}
