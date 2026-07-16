package idu.sba.backend.domain.context.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Range;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import idu.sba.backend.domain.context.dto.CodeContextResponseDTO;
import idu.sba.backend.domain.context.dto.ContextExtractionRequestDTO;
import idu.sba.backend.domain.context.dto.FieldSummaryDTO;
import idu.sba.backend.domain.context.dto.MethodSummaryDTO;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ContextExtractionServiceImpl implements ContextExtractionService {

    private final JavaParser parser = new JavaParser();

    @Override
    public CodeContextResponseDTO extract(ContextExtractionRequestDTO request) {

        String fileName = request.getFileName();
        Integer startLine = request.getStartLine();
        //diff 한 덩어리(hunk) 범위 지원 — endLine 없으면 단일 라인으로 취급
        Integer endLine = request.getEndLine() != null ? request.getEndLine() : startLine;

        //1단계: 전체 파일(패키지+import+클래스) 가정
        Optional<CompilationUnit> cuOpt = tryParse(request.getCode());
        //2단계: 클래스 없이 메서드/필드만 붙여넣은 경우
        if (cuOpt.isEmpty()) {
            cuOpt = tryParse("class __Snippet { " + request.getCode() + " }");
        }
        //3단계: 단순 문장 조각인 경우
        if (cuOpt.isEmpty()) {
            cuOpt = tryParse("class __Snippet { void __m() { " + request.getCode() + " } }");
        }

        if (cuOpt.isEmpty()) {
            return CodeContextResponseDTO.builder()
                    .filePath(fileName)
                    .imports(List.of())
                    .siblingMethods(List.of())
                    .fields(List.of())
                    .referencedLocalTypes(List.of())
                    .parseWarning("코드를 파싱할 수 없어 컨텍스트를 추출하지 못했습니다.")
                    .build();
        }

        CompilationUnit cu = cuOpt.get();

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse(null);

        List<String> imports = cu.getImports().stream()
                .map(imp -> imp.getNameAsString())
                .toList();

        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        ClassOrInterfaceDeclaration target = pickTargetClass(classes, startLine, endLine);
        String className = target != null ? target.getNameAsString() : null;

        List<MethodDeclaration> methods = target != null ? target.getMethods() : List.of();
        MethodDeclaration enclosingMethod = startLine != null
                ? methods.stream().filter(m -> overlapsRange(m.getRange(), startLine, endLine)).findFirst().orElse(null)
                : null;

        //최소 컨텍스트: 형제 메서드는 시그니처만(비용 절감), enclosing 메서드만 javadoc 포함
        List<MethodSummaryDTO> siblingMethods = methods.stream()
                .filter(m -> m != enclosingMethod)
                .map(m -> toMethodSummary(m, false))
                .toList();

        MethodSummaryDTO enclosingSummary = enclosingMethod != null ? toMethodSummary(enclosingMethod, true) : null;

        List<FieldSummaryDTO> fields = target != null
                ? target.getFields().stream().map(this::toFieldSummary).toList()
                : List.of();

        List<String> referencedLocalTypes = extractReferencedLocalTypes(cu, target, enclosingMethod);

        return CodeContextResponseDTO.builder()
                .filePath(fileName)
                .packageName(packageName)
                .imports(imports)
                .className(className)
                .enclosingMethod(enclosingSummary)
                .siblingMethods(siblingMethods)
                .fields(fields)
                .referencedLocalTypes(referencedLocalTypes)
                .parseWarning(null)
                .build();
    }

    private Optional<CompilationUnit> tryParse(String source) {
        ParseResult<CompilationUnit> result = parser.parse(source);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return result.getResult();
        }
        return Optional.empty();
    }

    //[startLine, endLine] 범위와 겹치는 클래스를 우선 선택(중첩 클래스 대응), 없으면 첫 번째 클래스
    private ClassOrInterfaceDeclaration pickTargetClass(List<ClassOrInterfaceDeclaration> classes, Integer startLine, Integer endLine) {
        if (classes.isEmpty()) return null;
        if (startLine != null) {
            return classes.stream()
                    .filter(c -> overlapsRange(c.getRange(), startLine, endLine))
                    .findFirst()
                    .orElse(classes.get(0));
        }
        return classes.get(0);
    }

    //diff hunk([startLine, endLine])와 노드 범위가 겹치는지 확인
    private boolean overlapsRange(Optional<Range> range, int startLine, int endLine) {
        return range.map(r -> startLine <= r.end.line && endLine >= r.begin.line).orElse(false);
    }

    private MethodSummaryDTO toMethodSummary(MethodDeclaration method, boolean includeJavadoc) {
        String javadoc = includeJavadoc
                ? method.getJavadocComment().map(JavadocComment::getContent).orElse(null)
                : null;
        Range range = method.getRange().orElse(Range.range(0, 0, 0, 0));
        return MethodSummaryDTO.of(method.getDeclarationAsString(), javadoc, range.begin.line, range.end.line);
    }

    private FieldSummaryDTO toFieldSummary(FieldDeclaration field) {
        Range range = field.getRange().orElse(Range.range(0, 0, 0, 0));
        return FieldSummaryDTO.of(field.toString().strip(), range.begin.line);
    }

    //이름 매칭 기반 best-effort 추출(심볼 해석 없음, 지역변수가 타입명과 같으면 오탐 가능)
    private List<String> extractReferencedLocalTypes(CompilationUnit cu, ClassOrInterfaceDeclaration target, MethodDeclaration enclosingMethod) {
        Node scope = enclosingMethod != null ? enclosingMethod : (target != null ? target : cu);

        Set<String> declaredNames = new HashSet<>();
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> declaredNames.add(c.getNameAsString()));
        cu.findAll(MethodDeclaration.class).forEach(m -> declaredNames.add(m.getNameAsString()));

        Set<String> referenced = new HashSet<>();
        scope.findAll(ClassOrInterfaceType.class).forEach(t -> referenced.add(t.getNameAsString()));
        scope.findAll(NameExpr.class).forEach(n -> referenced.add(n.getNameAsString()));
        scope.findAll(MethodCallExpr.class).forEach(c -> referenced.add(c.getNameAsString()));

        referenced.retainAll(declaredNames);
        return referenced.stream().sorted().toList();
    }

}
