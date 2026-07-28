/*
 * 리뷰 스튜디오 (LOC-002~003 + FR-38/47~49 통합 작업대)
 *
 * 한 화면에서 이어지는 흐름:
 *   ① 소스 고르기 — [PR 가져오기](연동 레포 PR → 파일 다중 선택/전체) 또는 [직접 붙여넣기]
 *   ② 모델 고르고 리뷰 시작 — 시작하면 모델이 잠긴다 (대화 맥락이 모델에 묶이므로,
 *      바꾸려면 [새 리뷰]. 잠긴 셀렉트에 안내 문구가 뜬다)
 *   ③ 코기와 채팅 — 이슈 말풍선 → 후속 질문 (리뷰·질문 모두 ⚡1, 남발은 일일 한도가 방어)
 *   ④ 프론트 코드가 있으면 상단 [▶ 미리보기] 토글 — 드래그·속성 편집이 코드로 실시간 반영
 *
 * 백엔드 코드도 리뷰는 전부 되고, 미리보기만 HTML/CSS/JS 로 제한된다 (iframe 한계선).
 * PR 상세(팀장 승인·내보내기)는 별도 페이지 유지 — 거기서 [스튜디오에서 이어가기]로 넘어온다.
 */
import { useEffect, useRef, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import * as api from "../../api/client";
import { useAuth } from "../../context/AuthContext";
import { useGame } from "../../context/GameContext";
import { PageHead, SevChip, renderDescription } from "../../components/ui";
import { renderQuestion } from "../../components/cardText";
import { MODEL_TIERS, PLAN_TIER, catKo } from "../../data/constants";
import PreviewDock from "./PreviewDock";
import useIsMobile from "../../hooks/useIsMobile";
import MobileStudio from "../mobile/MobileStudio";

const isFrontend = (f) => /\.(html|css)$/i.test(f.path) || f.kind === "frontend";
// 미리보기는 iframe에 문서를 통째로 넣는 방식이라 뿌리가 될 수 있는 건 HTML뿐이다.
// CSS·JS만 있는 PR을 열면 코드 글자만 나오니 자동으로 펴지 않는다 (그건 자산으로 딸려 붙는다)
const isPreviewable = (f) => /\.html?$/i.test(f.path);
// 붙여넣은 글이 HTML 문서인지. 예전엔 /<[a-z][^>]*>/ 하나로 봤는데 자바 제네릭(List<String>)이나
// 부등호가 든 코드까지 다 걸려서, 프론트가 한 줄도 없는 PR에도 "미리보기를 여세요"가 떴다.
// 문서 뿌리가 될 만한 태그가 실제로 있어야 미리보기가 뜬다
const looksHtml = (t) =>
  /<(!doctype\s+html|html|head|body|div|section|main|header|footer|nav|form|table|ul|ol|p|span|button|img|h[1-6])\b/i.test(t);
// 작업대 상태를 담아두는 자리. 탭 하나에 스튜디오 하나라 키도 하나면 된다
const STUDIO_KEY = "cogi-studio";

// 폰이면 데스크톱 본체를 아예 mount하지 않는다.
// 한 컴포넌트 안에서 갈랐더니 조건부 return 위의 useEffect가 그대로 돌아 같은 API를 두 번 때렸다.
// getWeaknessStats처럼 조회할 때마다 통계를 지우고 다시 넣는 API는 두 번 겹치면 한쪽이 빈 목록을 받는다.
export default function Studio() {
  return useIsMobile() ? <MobileStudio /> : <DesktopStudio />;
}

function DesktopStudio() {
    const { user } = useAuth();
    const { spendCredit, refundCredit, notify, S, creditLimit } = useGame();
    const location = useLocation();
    const fileRef = useRef(null);
    const threadRef = useRef(null); // 채팅 스레드 컨테이너 — 내부 스크롤 전용
    const loadedPrRef = useRef(false); // PR 판정 이어받기 — StrictMode 이중 실행에도 한 번만
    const myTier = PLAN_TIER[user.planName] ?? 1;
    const modelWeight = (m) => MODEL_TIERS.find((t) => t.name === m)?.tier ?? 1; // 크레딧 차감 가중치 = 모델 tier
    const remainingCredit = creditLimit - S.creditUsed;

    // 약점 통계 보러 갔다 오면 대화가 통째로 날아갔다. 화면을 떠나면 컴포넌트가 언마운트되기 때문.
    // 탭 안에서만 살아 있으면 되는 작업대라 sessionStorage에 담는다 — 탭을 닫으면 자연히 비워진다
    const saved = (() => {
        try { return JSON.parse(sessionStorage.getItem(STUDIO_KEY) || "null") ?? {}; }
        catch { return {}; }
    })();

    const [msgs, setMsgs] = useState(saved.msgs ?? []);
    const [input, setInput] = useState(saved.input ?? "");
    const [model, setModel] = useState(saved.model ?? MODEL_TIERS[0].name);
    const [busy, setBusy] = useState(false);
    const [reviewId, setReviewId] = useState(saved.reviewId ?? null); // null = 아직 시작 전 (모델 변경 가능)
    const [verdicts, setVerdicts] = useState(saved.verdicts ?? {}); // #6 이슈별 판정 { [issueId]: 'RESOLVED' | 'IGNORED' }
    const [finalized, setFinalized] = useState(saved.finalized ?? false); // "리뷰 완료" 버튼 재클릭 방지 — 한 번 확정하면 다시 못 누르게
    const [isPrReview, setIsPrReview] = useState(saved.isPrReview ?? false); // 이번 리뷰가 PR 가져오기로 시작됐는지 — 완료 안내 문구 분기용
    const [picker, setPicker] = useState(null); // PR 가져오기 모달 — 레포→PR→파일 3단계 { step, repos, repo, prs, pr, files, checked:Set }
    const [previewCode, setPreviewCode] = useState(saved.previewCode ?? null); // 미리보기 대상 (프론트 파일)
    const [dockOpen, setDockOpen] = useState(saved.dockOpen ?? false);
    const [previewRepoId, setPreviewRepoId] = useState(saved.previewRepoId ?? null); // 미리보기가 부족한 파일을 찾을 레포
    const [favSkills, setFavSkills] = useState(null); // 즐겨찾기 스킬. null = 아직 안 받음
    const [favOpen, setFavOpen] = useState(false); // 즐겨찾기 목록 열림 여부
    const [chatFull, setChatFull] = useState(false); // 채팅 전체보기
    const [reverifyByIssue, setReverifyByIssue] = useState({}); // 이슈 재검증 { [issueId]: {open, code, busy, result} } — 이슈별로 독립이라 issue가 바뀌면 자연히 새 항목
    const setReverifyFor = (issueId, patch) =>
        setReverifyByIssue((prev) => ({
            ...prev,
            [issueId]: { ...(prev[issueId] || { open: false, code: "", busy: false, result: null }), ...patch },
        }));
    const actionCost = reviewId === null ? modelWeight(model) : 1; // 리뷰 시작=모델 등급, 후속 질문=1
    const insufficientCredit = remainingCredit < actionCost;

    // 새 말풍선 → 채팅 영역 '안에서만' 아래로. scrollIntoView 는 페이지 전체를 끌고 내려가서 금지
    useEffect(() => {
        const t = threadRef.current;
        if (t) t.scrollTop = t.scrollHeight; // scrollTo 미지원 환경도 안전
    }, [msgs, busy]);

    const push = (m) => setMsgs((prev) => [...prev, m]);

    // 대화·리뷰 맥락을 담아둔다. 다른 화면 갔다 와도 이어서 쓸 수 있게
    // busy와 picker는 일부러 뺐다 — 떠나는 순간 끝난 상태라 되살리면 버튼이 잠긴 채로 뜬다
    useEffect(() => {
        try {
            sessionStorage.setItem(STUDIO_KEY, JSON.stringify({
                msgs, input, model, reviewId, verdicts, finalized, isPrReview,
                previewCode, dockOpen, previewRepoId,
            }));
        } catch { /* 용량 초과 등 — 저장 못 해도 화면은 그대로 돈다 */ }
    }, [msgs, input, model, reviewId, verdicts, finalized, isPrReview, previewCode, dockOpen, previewRepoId]);

    // 즐겨찾기 목록은 [★ 즐겨찾기]를 열 때 받는다. 열 때마다 다시 받아야
    // 스킬 추천 화면에서 방금 별을 단 게 이 목록에도 바로 뜬다
    const openFavSkills = async () => {
        if (favOpen) return setFavOpen(false);
        setFavOpen(true);
        try { setFavSkills(await api.getFavoriteSkills()); }
        catch { setFavSkills([]); } // 목록만 비운다 — 채팅 자체를 막을 이유는 없다
    };

    // 스킬을 누르면 질문 문장이 입력창에 채워진다. 보내기 전에 고칠 수 있다.
    // AI가 만들어 준 스킬은 붙여넣을 프롬프트를 들고 있으니 그걸 그대로 쓰고,
    // 큐레이션 스킬은 prompt가 없어서 제목으로 문장을 만든다.
    // 리뷰 시작 전에는 붙여넣은 코드가 이미 있을 수 있어 덮지 않고 뒤에 붙인다
    const useFavSkill = (s) => {
        const line = s.prompt?.trim() || `${s.title} 관점에서 이 코드를 점검해줘.`;
        setInput((prev) => (prev.trim() ? `${prev.trimEnd()}\n\n${line}` : line));
        setFavOpen(false);
    };

    // PR 상세 화면(PrDetail.tsx)의 [스튜디오에서 마저 판정]에서 prId를 받으면, 새 리뷰를
    // 시작하는 대신 그 PR의 최신 리뷰에서 아직 판정 안 한(OPEN) 이슈만 불러와 이어서 판정한다.
    // RESOLVED/IGNORED/PENDING은 절대 다시 안 불러옴 — finalizeIssue가 상태와 무관하게 덮어써서,
    // 이미 팀장 승인 대기 중인 이슈를 다시 판정에 태우면 안 되기 때문.
    useEffect(() => {
        const prId = location.state?.prId;
        if (!prId || loadedPrRef.current) return;
        loadedPrRef.current = true;
        (async () => {
            const res = await api.getPrReview(prId);
            const openIssues = res.issues.filter((it) => it.status === "OPEN");
            if (openIssues.length === 0) {
                notify("이 PR엔 판정할 지적이 없어요.");
                return;
            }
            setReviewId(res.reviewHistory?.[0]?.reviewId ?? null);
            setIsPrReview(true);
            push({ who: "cogi", text: `PR 판정을 이어갈게요 — 아직 결정 안 한 지적 ${openIssues.length}건이에요.` });
            openIssues.forEach((it) => push({ issue: it }));
        })();
    }, [location.state]);
    const cogiSays = (list, gap = 650) => {
        list.forEach((m, i) =>
            setTimeout(() => push({ who: "cogi", ...m }), gap * (i + 1)),
        );
        setTimeout(() => setBusy(false), gap * (list.length + 1));
    };

    /* ── ① PR 가져오기: 레포 선택 → PR 선택 → 파일 선택, 3단계 피커 ── */
    const openPicker = async () => {
        // 연동해 둔 레포 + GitHub의 실제 레포를 함께 보여준다.
        // 연동분만 띄우면 아직 안 붙인 레포의 PR은 가져올 길이 없었다
        const [linked, mine] = await Promise.all([
            api.getMyLinkedRepos().catch(() => []),
            api.getGithubRepos().catch(() => []), // GitHub 미연동이면 400 — 그땐 연동분만
        ]);
        const repoIdByGh = Object.fromEntries(linked.map((r) => [String(r.githubRepoId), r.repoId]));
        // GitHub 목록을 기준으로 세우고, 이미 붙인 건 repoId를 달아둔다
        const repos = mine.map((g) => ({
            githubRepoId: g.githubRepoId,
            repoName: g.repoName,
            repoId: repoIdByGh[String(g.githubRepoId)] ?? null,
        }));
        // 초대로 들어온 팀 레포처럼 내 GitHub 목록엔 없지만 연동된 것도 빠뜨리지 않는다
        linked.forEach((r) => {
            if (!repos.some((x) => String(x.githubRepoId) === String(r.githubRepoId))) {
                repos.push({ githubRepoId: r.githubRepoId, repoName: r.repoName, repoId: r.repoId });
            }
        });
        if (repos.length === 0) {
            notify("레포가 없어요. 마이페이지에서 GitHub을 먼저 연동해주세요.");
            return;
        }
        setPicker({ step: "repo", repos }); // 기본: 레포 선택 단계부터
    };

    const selectRepo = async (repo) => {
        // 아직 안 붙은 레포면 여기서 붙이고 이어간다. 레포 연동 화면까지 갔다 올 필요 없게
        let repoId = repo.repoId;
        try {
            if (!repoId) repoId = (await api.linkRepo(repo.githubRepoId)).repoId;
            const prs = await api.getRepoPrs(repoId);
            setPicker((p) => ({ ...p, step: "pr", repo: { ...repo, repoId }, prs }));
        } catch (e) {
            // 남이 이미 연동한 레포(409)거나 GitHub 조회 실패 — 피커는 열어둔 채 이유만 알린다
            notify(e.message || "이 레포의 PR을 가져오지 못했어요");
        }
    };

    const selectPr = async (pr) => {
        const files = await api.getRepoPrFiles(picker.repo.repoId, pr.number);
        setPicker((p) => ({
            ...p,
            step: "files",
            pr,
            files,
            checked: new Set(files.map((f) => f.path)), // 기본 전체 선택
        }));
    };

    // PR에서 가져온 파일로 미리보기를 연다.
    // 주의 — 피커가 준 f.code는 전체 내용이 아니라 GitHub의 patch(unified diff)다.
    // diff를 그대로 iframe에 넣으면 +/- 붙은 글자만 나온다. 그래서 GitHub에서 원본을 다시 받는다
    const openPreviewFor = async (files, repoId) => {
        const front = files.find(isPreviewable);
        if (!front) return; // 볼 만한 화면 파일이 없으면 도크를 굳이 열지 않는다
        setDockOpen(true);
        setPreviewCode(front.code); // 원본이 오기 전까지는 diff라도 띄워둔다
        try {
            const file = await api.getRepoFileContent(repoId, front.path);
            if (file?.content) setPreviewCode(file.content);
        } catch (e) {
            notify(e.message || "원본 파일을 못 받아 diff 상태로 보여드려요");
        }
    };

    const importFiles = () => {
        const files = picker.files.filter((f) => picker.checked.has(f.path));
        const { repo, pr } = picker;
        setPicker(null);
        if (files.length === 0) return;
        setPreviewRepoId(repo.repoId); // 미리보기가 부족한 파일을 이 레포에서 찾는다
        openPreviewFor(files, repo.repoId); // 디자인이 바로 보이게 도크 자동 오픈
        runReview(files.map((f) => `// ${f.path}\n${f.code}`).join("\n\n"), files, {
            repoId: repo.repoId,
            prNumber: pr.number,
            title: pr.title,
            authorLogin: pr.authorLogin,
        });
    };

    /* ── ② 리뷰 시작 — 여기서부터 모델 잠금.
     * PR 가져오기로 시작한 리뷰는 target_type=PR로 저장돼(prMeta 있으면 reviewImportedPr 호출)
     * "PR 리뷰"(/app/prs) 목록에도 실제로 뜬다 — 직접 붙여넣기/업로드는 그대로 PASTE/UPLOAD. ── */
    const runReview = async (
        codeText: string,
        files?: any[],
        prMeta?: { repoId: number; prNumber: number; title?: string; authorLogin?: string },
    ) => {
        if (!spendCredit(modelWeight(model))) return;
        // 안내 문구는 "미리보기를 실제로 열 수 있을 때"만 띄운다.
        // isFrontend(css 포함)로 보면 CSS만 있는 PR에도 뜨는데, 그건 뿌리 문서가 없어 못 연다
        const hasFront = files ? files.some(isPreviewable) : looksHtml(codeText);
        if (files) {
            push({
                who: "me",
                text: `🐙 PR #${prMeta.prNumber} 에서 ${files.length}개 파일 가져옴:\n${files.map((f) => "· " + f.path).join("\n")}`,
            });
            setIsPrReview(true);
        } else push({ who: "me", text: codeText, isCode: true });
        setBusy(true);
        try {
            const res = prMeta
                ? await api.reviewImportedPr(prMeta.repoId, prMeta.prNumber, codeText, model, prMeta.title, prMeta.authorLogin)
                : await api.pasteReview(codeText, model);
            setReviewId(res.reviewId ?? 1);
            if (res.analyzable === false) {
                // 코드가 아니거나 분석 불가한 입력 — 서버가 크레딧을 이미 환불했으니 로컬 표시도 맞춘다
                refundCredit(modelWeight(model));
                cogiSays([
                    { text: res.summary || "코드가 아니라서 분석할 수 없었어요. 크레딧은 안 깎였어요." },
                ]);
            } else {
                cogiSays([
                    {
                        text: `${MODEL_TIERS.find((m) => m.name === model)?.label}(으)로 봤어요. 짚을 게 ${res.issues.length}건!`,
                    },
                    ...res.issues.map((it) => ({ issue: it })),
                    {
                        text: hasFront
                            ? "프론트 코드가 있네요 — 위의 [▶ 미리보기]를 열면 화면을 직접 만지면서 고칠 수 있어요."
                            : "더 궁금한 건 그대로 물어보세요. 같은 유형 3회면 약점 통계로 승격돼요.",
                    },
                ]);
            }
        } catch {
            refundCredit(modelWeight(model)); // 서버도 @Transactional 롤백으로 안 썼으니 로컬도 원복
            push({ who: "cogi", text: "리뷰 처리 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 시도해주세요." });
            setBusy(false);
        }
    };

    /* ── ③ 후속 질문 — 이것도 AI 호출이라 ⚡1 ── */
    const runQuestion = async (q) => {
        if (!spendCredit(1)) return;
        push({ who: "me", text: q });
        setBusy(true);
        try {
            const res = await api.askReviewQuestion(reviewId, q);
            push({ who: "cogi", text: res.answer });
        } catch {
            refundCredit(1);
            push({ who: "cogi", text: "답변 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 물어봐주세요." });
        } finally {
            setBusy(false);
        }
    };

    const send = () => {
        const v = input.trim();
        if (!v || busy) return;
        setInput("");
        if (reviewId === null) {
            // 붙여넣은 게 HTML 이면 미리보기 대상으로도 잡아준다
            if (looksHtml(v)) setPreviewCode(v);
            runReview(v);
        } else runQuestion(v);
    };

    const onFile = async (f) => {
        if (!f || busy || reviewId !== null) return;
        if (!spendCredit(modelWeight(model))) return;
        push({ who: "me", text: `📁 ${f.name}` });
        setBusy(true);
        try {
            const res = await api.uploadReview(f, model);
            setReviewId(res.reviewId ?? 1);
            if (res.analyzable === false) {
                refundCredit(modelWeight(model));
                cogiSays([
                    { text: res.summary || "코드가 아니라서 분석할 수 없었어요. 크레딧은 안 깎였어요." },
                ]);
            } else {
                cogiSays([
                    { text: `파일 잘 받았어요! 짚을 게 ${res.issues.length}건.` },
                    ...res.issues.map((it) => ({ issue: it })),
                ]);
            }
        } catch {
            refundCredit(modelWeight(model));
            push({ who: "cogi", text: "리뷰 처리 중 문제가 생겼어요. 크레딧은 안 깎였어요 — 다시 시도해주세요." });
            setBusy(false);
        }
    };

    /* ── 새 리뷰: 모든 맥락 리셋 → 모델 다시 선택 가능 ── */
    const resetAll = () => {
        sessionStorage.removeItem(STUDIO_KEY); // 담아둔 것도 같이 버린다
        setMsgs([]);
        setReviewId(null);
        setPreviewCode(null);
        setDockOpen(false);
        setPreviewRepoId(null); // 새 리뷰는 레포도 다시 정해진다
        setInput("");
        setFinalized(false);
        setIsPrReview(false);
    };


    return (
        <main className="app-main">
            <PageHead
                badge="STUDIO"
                badgeCls="co"
                title="리뷰 스튜디오"
                lead={
                    "PR을 가져오거나 코드를 붙여넣고, 코기와 대화하며 다듬는 작업대예요.\n프론트 코드는 미리보기를 열어 직접 만질 수 있어요."
                }
            />

            {/* ── 미리보기 도크: 채팅 상단, 접었다 펴는 토글 (FR-47~49) ── */}
            {previewCode !== null && (
                <div className="panel flush" style={{ marginBottom: 18 }}>
                    <button
                        className="dock-toggle"
                        onClick={() => setDockOpen((o) => !o)}
                    >
                        {dockOpen ? "▼ 미리보기 접기" : "▶ 미리보기 열기"}
                        <span className="note sm">
              드래그·색상·크기·글자까지 — 편집하면 코드가 실시간으로 바뀝니다
            </span>
                    </button>
                    {dockOpen && (
                        <PreviewDock code={previewCode} onCode={setPreviewCode} repoId={previewRepoId} />
                    )}
                </div>
            )}

            <div className={`panel flush chat-panel ${chatFull ? "full" : ""}`}>
                {/* 스레드가 720px에 갇혀 답답하다. 모양은 그대로 두고 화면만 넓힌다 */}
                <div className="chat-head">
                    <button type="button" className="chat-full-btn" onClick={() => setChatFull((v) => !v)}>
                        {chatFull ? "⤡ 원래 크기" : "⤢ 전체보기"}
                    </button>
                </div>
                <div className="chat-thread" ref={threadRef}>
                    {msgs.length === 0 && (
                        /* ── ① 소스 선택 (빈 상태) ── */
                        <div className="empty">
                            <p style={{ fontSize: 26 }}>🐕</p>
                            <p style={{ marginBottom: 18 }}>
                                어떤 코드를 봐드릴까요? 백엔드든 프론트든 다 환영이에요.
                                <br />
                                GitHub PR을 가져오거나, 아래 입력창에 바로 코드를 붙여넣으세요.
                            </p>
                            {insufficientCredit && (
                                <p className="model-hint" style={{ marginBottom: 12 }}>
                                    ⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요
                                </p>
                            )}
                            <div className="row" style={{ justifyContent: "center" }}>
                                <button
                                    className="btn co sm"
                                    onClick={openPicker}
                                    disabled={insufficientCredit}
                                >
                                    🐙 GitHub PR 가져오기
                                </button>
                            </div>
                        </div>
                    )}
                    {msgs.map((m, i) =>
                            m.issue ? (
                                <div key={i} className="chat-bubble cogi">
                                    <div className="row" style={{ gap: 8, marginBottom: 8 }}>
                                        <SevChip sev={m.issue.severity} />
                                        <span className="chip navy">{catKo(m.issue.category)}</span>
                                    </div>
                                    {m.issue.codeSnippet && (
                                        <pre className="codebox snippet">
                    {m.issue.codeSnippet.join("\n")}
                  </pre>
                                    )}
                                    <div className="issue-desc">{renderDescription(m.issue.description)}</div>
                                    {/* #6 이슈 판정은 아래 고정 배너에서만 함 — 채팅창은 기록 보기 전용(읽기 전용) */}
                                    {verdicts[m.issue.id] && (
                                        <span
                                            className={`chip ${verdicts[m.issue.id] === "RESOLVED" ? "low" : "mid"}`}
                                            style={{ marginTop: 10, display: "inline-block" }}
                                        >
                    {verdicts[m.issue.id] === "RESOLVED"
                        ? "✔ 고치기로 함 (해결 요청)"
                        : "🙋 의도한 코드 (무시 요청)"}
                  </span>
                                    )}
                                </div>
                            ) : (
                                <div key={i} className={`chat-bubble ${m.who}`}>
                                    {m.isCode ? (
                                        <pre>{m.text}</pre>
                                    ) : m.who === "cogi" ? (
                                        // 후속 답변은 마크다운으로 온다. 그냥 뿌리면 ```펜스와 #제목이 글자로 찍힌다
                                        renderQuestion(m.text)
                                    ) : (
                                        <p style={{ whiteSpace: "pre-line" }}>{m.text}</p>
                                    )}
                                </div>
                            ),
                    )}
                    {busy && (
                        <div className="chat-bubble cogi typing">
                            {reviewId === null ? "🔍 코드 분석 중" : "🤔 답변 작성 중"}
                            <i>.</i>
                            <i>.</i>
                            <i>.</i>
                        </div>
                    )}
                </div>

                {/* #6 완료 배너 — 스레드 밖(하단 고정)이라 이슈 말풍선이 항상 위에 보인다 */}
                {(() => {
                    const issues = msgs.filter((m) => m.issue).map((m) => m.issue);
                    if (issues.length === 0) return null;
                    const done = issues.filter((it) => verdicts[it.id]).length;
                    const all = done === issues.length;
                    const next = issues.find((it) => !verdicts[it.id]); // 다음 미결정 이슈
                    return (
                        <div className={`studio-done ${all ? "ready" : ""}`}>
                            {all ? (
                                <span>{`✅ 지적 ${issues.length}건을 모두 확인했어요. 아래 버튼으로 리뷰를 마치면 판정이 저장돼요.`}</span>
                            ) : (
                                <div className="next-issue">
                                    <div
                                        className="row"
                                        style={{ gap: 8, alignItems: "center", flexWrap: "wrap" }}
                                    >
                                        <b>{`${done}/${issues.length} 결정`}</b>
                                        <SevChip sev={next.severity} />
                                        <span className="chip navy">{catKo(next.category)}</span>
                                        <span className="mono xs">
                      {next.filePath}:{next.lineNumber}
                    </span>
                                    </div>
                                    {/* 설명·코드는 위 채팅 기록에 이미 있음 — 여긴 빠른 결정용이라 슬림하게 유지 */}
                                    <div className="row" style={{ gap: 8 }}>
                                        <button
                                            className="btn wh sm"
                                            onClick={() =>
                                                setVerdicts((v) => ({ ...v, [next.id]: "IGNORED" }))
                                            }
                                        >
                                            🙋 의도했어요
                                        </button>
                                        <button
                                            className="btn co sm"
                                            onClick={() =>
                                                setVerdicts((v) => ({ ...v, [next.id]: "RESOLVED" }))
                                            }
                                        >
                                            ✔ 고칠게요
                                        </button>
                                        <button
                                            className="btn wh sm"
                                            onClick={() => {
                                                const rv = reverifyByIssue[next.id];
                                                setReverifyFor(next.id, { open: !rv?.open, result: null });
                                            }}
                                        >
                                            🔁 수정한 코드로 확인
                                        </button>
                                    </div>
                                    {/* 이슈 재검증 [설계 추론] — 참고용 판정만 보여줄 뿐, 판정(RESOLVED/IGNORED)은
                                        여전히 위 버튼으로 직접 눌러야 확정됨(승인 흐름을 우회하지 않으려는 의도) */}
                                    {reverifyByIssue[next.id]?.open && (() => {
                                        const rv = reverifyByIssue[next.id];
                                        return (
                                            <div style={{ marginTop: 8 }}>
                                                <textarea
                                                    rows={4}
                                                    placeholder="수정한 코드를 붙여넣으세요"
                                                    value={rv.code}
                                                    onChange={(e) => setReverifyFor(next.id, { code: e.target.value })}
                                                    style={{ width: "100%" }}
                                                />
                                                <div className="row" style={{ gap: 8, marginTop: 6, alignItems: "center" }}>
                                                    <button
                                                        className="btn co sm"
                                                        disabled={rv.busy || !rv.code.trim() || insufficientCredit}
                                                        onClick={async () => {
                                                            if (!spendCredit(1)) return;
                                                            setReverifyFor(next.id, { busy: true, result: null });
                                                            try {
                                                                const res = await api.reverifyIssue(next.id, rv.code);
                                                                setReverifyFor(next.id, { busy: false, result: res });
                                                            } catch {
                                                                refundCredit(1);
                                                                setReverifyFor(next.id, { busy: false, result: { error: true } });
                                                            }
                                                        }}
                                                    >
                                                        {rv.busy ? "확인 중…" : "확인하기 (⚡1)"}
                                                    </button>
                                                    {rv.result && !rv.result.error && (
                                                        <span className="note sm">
                                                            {rv.result.stillPresent ? "⚠️ 아직 남아있어요 — " : "✅ 고쳐진 것 같아요 — "}
                                                            {rv.result.explanation}
                                                        </span>
                                                    )}
                                                    {rv.result?.error && (
                                                        <span className="note sm">확인 중 문제가 생겼어요. 크레딧은 안 깎였어요.</span>
                                                    )}
                                                </div>
                                            </div>
                                        );
                                    })()}
                                </div>
                            )}
                            <button
                                className="btn co sm"
                                disabled={!all || busy || finalized}
                                onClick={async () => {
                                    setBusy(true);
                                    try {
                                        await Promise.all(
                                            issues.map((it) =>
                                                api.finalizeIssue(it.id, verdicts[it.id]),
                                            ),
                                        );
                                        setFinalized(true); // 재클릭 방지 — 같은 판정을 중복 저장할 이유가 없음
                                        // PR 가져오기로 만든 리뷰는 target_type=PR로 저장돼 "PR 리뷰"(/app/prs)에서도 보이지만,
                                        // 팀 결재(RDB-003)가 필요한 화면은 아니라서 강제로 이동시키지 않고 여기서 완료만 알린다.
                                        // 단, PR 리뷰의 CRITICAL 이슈를 "고칠게요"로 확정했다면 서버가 팀장 승인 대기로 돌려놓으므로 안내를 다르게 준다
                                        const hasCriticalResolveRequest = isPrReview && issues.some(
                                            (it) => verdicts[it.id] === "RESOLVED" && it.severity === "CRITICAL",
                                        );
                                        notify(hasCriticalResolveRequest
                                            ? "리뷰를 완료했어요. 심각도 높은 이슈는 팀장 승인 후 '해결됨'으로 반영돼요."
                                            : "리뷰를 완료했어요. 판정이 저장됐어요.");
                                    } finally {
                                        setBusy(false);
                                    }
                                }}
                            >
                                {finalized ? "완료됨" : "리뷰 완료"}
                            </button>
                        </div>
                    );
                })()}

                {/* ── 입력 영역: 입력창이 주인공(전체 폭), 도구는 아래 한 줄로 ── */}
                <div className="chat-input">
                    {/* 즐겨찾기해 둔 스킬 — 버튼으로 접어 둔다. 칩을 늘 깔아 두면 입력창이 아래로 밀린다.
                        리뷰 시작 전에도 연다 — 어떤 관점으로 볼지 코드와 같이 적어 보낼 수 있다 */}
                    <div className="chat-fav">
                            <button type="button" className={`chat-fav-btn ${favOpen ? "on" : ""}`} onClick={openFavSkills}>
                                ★ 즐겨찾기 스킬{favSkills?.length ? ` ${favSkills.length}` : ""}
                                <i>{favOpen ? "▲" : "▼"}</i>
                            </button>
                            {favOpen && (
                                favSkills === null ? (
                                    <p className="note sm chat-fav-note">불러오는 중…</p>
                                ) : favSkills.length === 0 ? (
                                    <p className="note sm chat-fav-note">
                                        아직 즐겨찾기한 스킬이 없어요.{" "}
                                        <Link to="/app/skills" className="link-line">AI 스킬 추천</Link>에서 별을 달면 여기 뜹니다.
                                    </p>
                                ) : (
                                    <div className="chat-skills">
                                        {favSkills.map((s) => (
                                            <button key={s.id} className="chat-skill" disabled={busy || insufficientCredit}
                                                    title={s.prompt ? "이 스킬의 프롬프트를 입력창에 넣습니다" : "이 스킬로 물어볼 문장을 넣습니다"}
                                                    onClick={() => useFavSkill(s)}>
                                                ★ {s.title}
                                            </button>
                                        ))}
                                    </div>
                                )
                        )}
                    </div>
          <textarea
              rows={reviewId === null ? 4 : 2}
              value={input}
              spellCheck={false}
              disabled={insufficientCredit}
              placeholder={
                  insufficientCredit
                      ? "오늘 크레딧을 다 썼어요 · 자정에 초기화돼요"
                      : reviewId === null
                          ? "리뷰 받을 코드를 붙여넣으세요 (Ctrl+Enter 전송)"
                          : "후속 질문을 입력하세요 (Ctrl+Enter 전송)"
              }
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => {
                  if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) send();
              }}
          />

                    <div className="chat-tools">
                        {/* 모델: 시작 전 자유 선택(상위 티어는 잠금 표시) / 시작 후 통째 잠금 + 안내 */}
                        <span className="model-wrap">
              <select
                  value={model}
                  disabled={reviewId !== null}
                  aria-label="AI 모델"
                  onChange={(e) => setModel(e.target.value)}
              >
                {MODEL_TIERS.map((m) => (
                    <option
                        key={m.name}
                        value={m.name}
                        disabled={m.tier > myTier}
                    >
                        {m.label}
                        {m.tier > myTier
                            ? ` — ${Object.keys(PLAN_TIER).find((k) => PLAN_TIER[k] === m.tier)} 필요`
                            : ""}
                    </option>
                ))}
              </select>
                            {reviewId !== null && (
                                <span className="model-hint">
                  모델을 바꾸려면 새 리뷰를 시작하세요
                </span>
                            )}
            </span>

                        {insufficientCredit && (
                            <span className="model-hint">
                오늘 크레딧 소진 · 자정 초기화
              </span>
                        )}
                        <span className="ml-auto" />
                        {reviewId === null ? (
                            <button
                                className="btn wh sm"
                                title={insufficientCredit ? "오늘 크레딧을 다 썼어요" : "파일 업로드"}
                                disabled={insufficientCredit}
                                onClick={() => fileRef.current?.click()}
                            >
                                📁 파일
                            </button>
                        ) : (
                            <button
                                className="btn wh sm"
                                title="맥락을 비우고 처음부터 — 모델도 다시 고를 수 있어요"
                                onClick={resetAll}
                            >
                                + 새 리뷰
                            </button>
                        )}
                        <button
                            className="btn co sm"
                            onClick={send}
                            disabled={busy || !input.trim() || insufficientCredit}
                        >
                            {reviewId === null ? `리뷰 시작 (⚡${modelWeight(model)})` : "질문 (⚡1)"}
                        </button>
                    </div>
                </div>
            </div>

            <input
                ref={fileRef}
                type="file"
                accept=".ts,.tsx,.js,.jsx,.java,.py,.kt,.go,.html,.css,.txt"
                hidden
                onChange={(e) => onFile(e.target.files?.[0])}
            />

            {/* ── PR 파일 선택 모달: 다중 선택 or 전체 ── */}
            {picker && (
                <div className="modal-mask" onClick={() => setPicker(null)}>
                    <div
                        className="modal wide"
                        role="dialog"
                        aria-modal="true"
                        onClick={(e) => e.stopPropagation()}
                    >
                        {/* 1단계: 레포 선택 */}
                        {picker.step === "repo" && (
                            <>
                                <h3>🐙 어느 레포의 PR을 가져올까요?</h3>
                                <p className="note sm" style={{ marginBottom: 14 }}>
                                    내 GitHub 레포예요. 아직 연동 안 한 것도 고르면 그 자리에서 붙여드려요.
                                </p>
                                {picker.repos.map((r) => (
                                    <div
                                        key={r.githubRepoId}
                                        className="check-row"
                                        style={{ marginBottom: 8 }}
                                        onClick={() => selectRepo(r)}
                                    >
                                        <span className="mono" style={{ fontSize: 12.5 }}>{r.repoName}</span>
                                        {/* 이미 붙은 것과 지금 붙일 것을 구분해 준다 */}
                                        <span className={`chip sm ${r.repoId ? "low" : "gray"}`}>
                                            {r.repoId ? "연동됨" : "연동 필요"}
                                        </span>
                                    </div>
                                ))}
                                <div className="row" style={{ marginTop: 20 }}>
                                    <button className="btn wh sm" onClick={() => setPicker(null)}>취소</button>
                                </div>
                            </>
                        )}

                        {/* 2단계: PR 선택 */}
                        {picker.step === "pr" && (
                            <>
                                <h3>🐙 {picker.repo.repoName} — 가져올 PR 선택</h3>
                                <p className="note sm" style={{ marginBottom: 14 }}>
                                    {picker.prs.length === 0 ? "열린 PR이 없어요." : "열린 PR 목록이에요."}
                                </p>
                                {picker.prs.map((pr) => (
                                    <div
                                        key={pr.number}
                                        className="check-row"
                                        style={{ marginBottom: 8 }}
                                        onClick={() => selectPr(pr)}
                                    >
                                        <span className="mono xs">#{pr.number}</span>
                                        <span style={{ fontSize: 13 }}>{pr.title}</span>
                                        {pr.authorLogin && (
                                            <span className="chip navy">@{pr.authorLogin}</span>
                                        )}
                                    </div>
                                ))}
                                <div className="row" style={{ marginTop: 20 }}>
                                    <button className="btn wh sm" onClick={() => setPicker((p) => ({ step: "repo", repos: p.repos }))}>
                                        ← 레포 다시 선택
                                    </button>
                                </div>
                            </>
                        )}

                        {/* 3단계: 파일 선택 */}
                        {picker.step === "files" && (
                            <>
                                <h3>🐙 PR #{picker.pr.number} — 가져올 파일 선택</h3>
                                <p className="note sm" style={{ marginBottom: 14 }}>
                                    여러 개 선택할 수 있어요. 프론트 파일이 포함되면 미리보기도
                                    열립니다.
                                </p>
                                <label
                                    className="check-row"
                                    style={{ fontWeight: 700, marginBottom: 10 }}
                                >
                                    <input
                                        type="checkbox"
                                        checked={picker.checked.size === picker.files.length}
                                        onChange={(e) =>
                                            setPicker((p) => ({
                                                ...p,
                                                checked: new Set(
                                                    e.target.checked ? p.files.map((f) => f.path) : [],
                                                ),
                                            }))
                                        }
                                    />
                                    전체 선택
                                </label>
                                {picker.files.map((f) => (
                                    <label
                                        key={f.path}
                                        className="check-row"
                                        style={{ marginBottom: 8 }}
                                    >
                                        <input
                                            type="checkbox"
                                            checked={picker.checked.has(f.path)}
                                            onChange={() =>
                                                setPicker((p) => {
                                                    const next = new Set(p.checked);
                                                    next.has(f.path) ? next.delete(f.path) : next.add(f.path);
                                                    return { ...p, checked: next };
                                                })
                                            }
                                        />
                                        <span className="mono" style={{ fontSize: 12.5 }}>
                      {f.path}
                    </span>
                                        <span className={`chip ${isFrontend(f) ? "low" : "navy"}`}>
                      {isFrontend(f) ? "프론트" : "백엔드"}
                    </span>
                                    </label>
                                ))}
                                {insufficientCredit && (
                                    <p className="model-hint" style={{ marginTop: 10 }}>
                                        ⚡ 오늘 크레딧을 다 썼어요 · 자정에 초기화돼요
                                    </p>
                                )}
                                <div className="row" style={{ marginTop: 20 }}>
                                    <button className="btn wh sm" onClick={() => setPicker(null)}>
                                        취소
                                    </button>
                                    <button
                                        className="btn co sm ml-auto"
                                        onClick={importFiles}
                                        disabled={picker.checked.size === 0 || insufficientCredit}
                                    >
                                        {picker.checked.size}개 가져와서 리뷰 (⚡{modelWeight(model)})
                                    </button>
                                </div>
                            </>
                        )}
                    </div>
                </div>
            )}

            <p className="note sm" style={{ marginTop: 14 }}>
                결과는 약점 통계에 쌓여요.{" "}
                <Link to="/app/weakness" className="link-line">
                    약점 통계 →
                </Link>{" "}
                · 팀 결재가 필요한 PR 이슈는{" "}
                <Link to="/app/prs" className="link-line">
                    PR 리뷰 →
                </Link>
            </p>
        </main>
    );
}