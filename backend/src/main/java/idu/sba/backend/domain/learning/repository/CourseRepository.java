package idu.sba.backend.domain.learning.repository;

import idu.sba.backend.domain.learning.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    // 약점 카테고리들에 매칭되는 강의 조회
    List<Course> findByCategoryIn(Collection<String> categories);
}
