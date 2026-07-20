package idu.sba.backend.global.ai;

//_common_rules.txt 0번 규칙이 참조하는 input_type 값. 프롬프트는 이 값을 항상 기대해왔지만
//PR 리뷰가 생기기 전까지는 실제로 전송된 적이 없었다(paste/upload/게스트는 전부 pasted_code로 취급).
public enum AiInputType {

    PASTED_CODE("pasted_code"), //붙여넣기/업로드/비로그인 체험 — 항상 전체 코드
    PR_DIFF("pr_diff");         //PR 웹훅 경로 — GitHub 변경분(unified diff)

    private final String wireValue;

    AiInputType(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

}
