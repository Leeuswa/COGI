package idu.sba.backend.prtestscratch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// ⚠️ 이 파일은 PR 리뷰 승인/알림 흐름을 실제 GitHub PR로 검증하기 위한 더미 파일입니다.
// 아무 곳에서도 호출되지 않으며, 검증이 끝나면 이 PR은 머지하지 않고 닫습니다.
public class VulnerableLookupExample {

    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    public ResultSet findUserByName(Connection conn, String userName) throws Exception {
        // PreparedStatement로 파라미터를 바인딩해서 쿼리 구조와 사용자 입력을 분리 — SQL 인젝션 차단
        String query = "SELECT * FROM users WHERE nickname = ?";
        PreparedStatement pstmt = conn.prepareStatement(query);
        pstmt.setString(1, userName);
        return pstmt.executeQuery();
    }
}
