package idu.sba.backend.domain.context.service;

import idu.sba.backend.domain.context.dto.CodeContextResponseDTO;
import idu.sba.backend.domain.context.dto.ContextExtractionRequestDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ContextExtractionServiceImplTest {

    private final ContextExtractionServiceImpl service = new ContextExtractionServiceImpl();

    //ContextExtractionRequestDTO는 setter가 없어(@RequestBody 전용) 리플렉션으로 필드를 채운다
    private ContextExtractionRequestDTO request(String code, Integer startLine, Integer endLine) {
        ContextExtractionRequestDTO dto = new ContextExtractionRequestDTO();
        setField(dto, "code", code);
        setField(dto, "startLine", startLine);
        setField(dto, "endLine", endLine);
        return dto;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void 전체_클래스는_1단계에서_바로_파싱되어_클래스명과_필드가_추출된다() {
        String code = "public class Foo { private int y; public int bar(int x){ return x+1; } }";

        CodeContextResponseDTO result = service.extract(request(code, null, null));

        assertThat(result.getParseWarning()).isNull();
        assertThat(result.getClassName()).isEqualTo("Foo");
        assertThat(result.getFields()).hasSize(1);
        assertThat(result.getSiblingMethods()).hasSize(1);
    }

    @Test
    void 클래스_없는_메서드_스니펫은_2단계_폴백으로_파싱된다() {
        String code = "public int bar(int x){ return x+1; }";

        CodeContextResponseDTO result = service.extract(request(code, null, null));

        assertThat(result.getParseWarning()).isNull();
        assertThat(result.getSiblingMethods()).hasSize(1);
        assertThat(result.getSiblingMethods().get(0).getSignature()).contains("bar");
    }

    @Test
    void 문장_조각은_3단계_폴백으로_파싱된다() {
        String code = "int a = 1 + 2;";

        CodeContextResponseDTO result = service.extract(request(code, null, null));

        assertThat(result.getParseWarning()).isNull();
        assertThat(result.getClassName()).isEqualTo("__Snippet");
    }

    @Test
    void 파싱_불가능한_코드는_parseWarning을_채워서_반환한다() {
        String code = "!!! not java at all ###";

        CodeContextResponseDTO result = service.extract(request(code, null, null));

        assertThat(result.getParseWarning()).isNotNull();
        assertThat(result.getClassName()).isNull();
        assertThat(result.getFields()).isEmpty();
    }

    @Test
    void startLine에_해당하는_메서드가_enclosingMethod로_분리되고_나머지는_sibling으로_분리된다() {
        String code = """
                public class Foo {
                    public void a() { }
                    public void b() { }
                }
                """;

        CodeContextResponseDTO result = service.extract(request(code, 3, null));

        assertThat(result.getEnclosingMethod()).isNotNull();
        assertThat(result.getEnclosingMethod().getSignature()).contains("b");
        assertThat(result.getSiblingMethods()).hasSize(1);
        assertThat(result.getSiblingMethods().get(0).getSignature()).contains("a");
    }

    @Test
    void startLine과_endLine_범위가_메서드와_겹치면_enclosingMethod로_잡힌다() {
        String code = """
                public class Foo {
                    public void a() {
                        int x = 1;
                        int y = 2;
                    }
                    public void b() { }
                }
                """;

        //diff hunk가 a() 메서드 중간(3~4번째 줄)만 걸치는 경우
        CodeContextResponseDTO result = service.extract(request(code, 3, 4));

        assertThat(result.getEnclosingMethod()).isNotNull();
        assertThat(result.getEnclosingMethod().getSignature()).contains("a");
    }

    @Test
    void enclosingMethod만_javadoc을_포함하고_sibling은_javadoc이_없다() {
        String code = """
                public class Foo {
                    /** a의 설명 */
                    public void a() { }
                    /** b의 설명 */
                    public void b() { }
                }
                """;

        //5번째 줄이 "public void b() { }" 선언 자체(javadoc 주석은 노드 범위에 포함되지 않음)
        CodeContextResponseDTO result = service.extract(request(code, 5, null));

        assertThat(result.getEnclosingMethod().getJavadoc()).contains("b의 설명");
        assertThat(result.getSiblingMethods()).hasSize(1);
        assertThat(result.getSiblingMethods().get(0).getJavadoc()).isNull();
    }

    @Test
    void referencedLocalTypes는_같은_파일에_선언된_이름만_이름매칭으로_추출한다() {
        String code = """
                public class Foo {
                    public void a() {
                        Helper h = new Helper();
                        b();
                    }
                    public void b() { }
                }
                class Helper { }
                """;

        CodeContextResponseDTO result = service.extract(request(code, 2, 5));

        assertThat(result.getReferencedLocalTypes()).contains("Helper", "b");
    }
}
