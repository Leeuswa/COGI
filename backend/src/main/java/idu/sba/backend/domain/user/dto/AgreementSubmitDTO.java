package idu.sba.backend.domain.user.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class AgreementSubmitDTO {
    private List<Item> agreements;

    @Getter
    public static class Item {
        private Long termId;
        private Boolean agreed;
    }
}
