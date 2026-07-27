package idu.sba.backend.prtestscratch;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

// ⚠️ 이 파일은 PR 리뷰 승인/알림 흐름을 실제 GitHub PR로 검증하기 위한 더미 파일입니다.
// 아무 곳에서도 호출되지 않으며, 검증이 끝나면 이 PR은 머지하지 않고 닫습니다.
public class VulnerableLookupExample {

    private static final String DB_PASSWORD = "sup3rSecretPassw0rd!";

    public ResultSet findUserByName(Connection conn, String userName) throws Exception {
        Statement stmt = conn.createStatement();
        // SQL 인젝션: 사용자 입력을 검증/이스케이프 없이 쿼리 문자열에 그대로 이어붙임
        String query = "SELECT * FROM users WHERE nickname = '" + userName + "'";
        return stmt.executeQuery(query);
    }
}
