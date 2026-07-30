/*
 * 모바일 전용 알림 종.
 * 데스크톱 Bell은 헤더 아래로 펼쳐지는 드롭다운이라 폰에서는 화면 밖으로 나간다.
 * 폰에서는 아래에서 올라오는 시트로 띄우고, 본문을 접지 않고 그대로 보여준다
 * (데스크톱은 목록에서 한 줄만 보이고 눌러야 팝업이 뜨는데, 폰에서는 한 번에 읽히는 게 낫다).
 *
 * 안 본 알림 배지는 데스크톱과 같은 localStorage 키를 쓴다 — 폰에서 열면 PC에서도 배지가 사라진다.
 */
import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import * as api from "../api/client";

export default function MobileBell({ loginId }) {
  const [list, setList] = useState([]);
  const [open, setOpen] = useState(false);
  const nav = useNavigate();

  const seenKey = `cogi-noti-seen-${loginId}`;
  const [seenId, setSeenId] = useState(() => Number(localStorage.getItem(seenKey)) || 0);
  const unseen = list.filter((n) => n.id > seenId).length;

  useEffect(() => { api.getNotifications(loginId).then(setList).catch(() => setList([])); }, [loginId]);

  // 여는 순간 모두 본 것으로 표시 → 배지 제거 (데스크톱과 같은 규칙)
  const openSheet = () => {
    if (list.length) {
      const maxId = Math.max(...list.map((n) => n.id));
      setSeenId(maxId);
      localStorage.setItem(seenKey, String(maxId));
    }
    setOpen(true);
  };

  const dismiss = (e, n) => {
    e.stopPropagation(); // 항목 탭(이동)과 분리
    api.dismissNotification(n.id).catch(() => {});
    setList((l) => l.filter((x) => x.id !== n.id));
  };

  // 링크가 있으면 그 화면으로. 학습 계획 알림은 /app/cards/3?step=2 처럼 단계까지 들고 있다
  const go = (n) => {
    if (!n.link) return;
    setOpen(false);
    nav(n.link);
  };

  return (
    <>
      <button type="button" className="mh-bell" onClick={openSheet} aria-label={`알림${unseen ? ` ${unseen}건` : ""}`}>
        🔔{unseen > 0 && <i>{unseen > 9 ? "9+" : unseen}</i>}
      </button>

      {open && (
        <>
          <div className="mnoti-dim" onClick={() => setOpen(false)} />
          <div className="mnoti-sheet" role="dialog" aria-label="알림">
            <div className="ms-grab" />
            <h2>🔔 알림</h2>

            {list.length === 0 ? (
              <p className="mnoti-empty">새 알림이 없어요.</p>
            ) : (
              <ul className="mnoti-list">
                {list.map((n) => (
                  <li key={n.id} className={n.link ? "go" : ""} onClick={() => go(n)}>
                    <span className="mn-icon">{n.icon}</span>
                    <div className="mn-body">
                      {n.title && <b>{n.title}</b>}
                      <p>{n.text}</p>
                      {n.link && <em>눌러서 바로가기 →</em>}
                    </div>
                    <button type="button" className="mn-x" aria-label="알림 삭제"
                      onClick={(e) => dismiss(e, n)}>✕</button>
                  </li>
                ))}
              </ul>
            )}

            <button className="btn co sm full" onClick={() => setOpen(false)}>닫기</button>
          </div>
        </>
      )}
    </>
  );
}
