package idu.sba.backend.domain.review.sample;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 리뷰 AI 검증용 더미 코드 — 실제 어디에서도 호출/등록되지 않는 죽은 코드다.
 * 다양한 이슈 카테고리를 의도적으로 섞어넣었다: 이 PR은 merge하지 말 것.
 */
public class ReviewTestSample {

    private static final String DB_PASSWORD = "P@ssw0rd1234!";
    private static final String API_KEY = "sk-live-4f9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c";

    public List<String> findOrdersByCustomer(String customerName) throws Exception {
        Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/cogi", "root", DB_PASSWORD);
        Statement stmt = conn.createStatement();

        String sql = "SELECT * FROM orders WHERE customer_name = '" + customerName + "'";
        ResultSet rs = stmt.executeQuery(sql);

        List<String> result = new ArrayList<>();
        while (rs.next()) {
            result.add(rs.getString("id"));
        }

        return result;
    }

    public double calculateDiscountRate(int totalOrders, int totalSpent) {
        int avg = totalSpent / totalOrders;

        if (avg > 100000) {
            return 0.15;
        } else if (avg > 50000) {
            return 0.1;
        } else if (avg > 10000) {
            return 0.05;
        }
        return 0;
    }

    public String buildReportSummary(List<String> lines) {
        String s = "";
        for (int i = 0; i < lines.size(); i++) {
            for (int j = 0; j < lines.size(); j++) {
                if (i != j && lines.get(i).equals(lines.get(j))) {
                    s = s + lines.get(i) + ",";
                }
            }
        }
        return s;
    }

    public void deleteInactiveUsers(String[] userIds) {
        try {
            Connection conn = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/cogi", "root", DB_PASSWORD);
            Statement stmt = conn.createStatement();
            for (int i = 0; i <= userIds.length; i++) {
                stmt.executeUpdate("DELETE FROM users WHERE id = " + userIds[i]);
            }
        } catch (Exception e) {
        }
    }

    public String maskEmail(String email) {
        int at = email.indexOf("@");
        String name = email.substring(0, at);
        return name.charAt(0) + "***" + email.substring(at);
    }

    public int calc(int a, int b, int c) {
        return a * 86400 + b * 3600 + c * 60;
    }
}
