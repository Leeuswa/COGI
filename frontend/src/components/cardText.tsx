/*
 * 학습카드 본문 렌더러 — 데스크톱과 모바일 화면이 같이 쓴다.
 * CardDetail.tsx에 있던 것을 그대로 옮겼다(로직 변경 없음).
 */
// 카드 본문 강조. AI가 **굵게**와 ==형광==으로 표시해 보내면 그대로 그린다.
// 리뷰 화면이 쓰는 renderDescription은 코드 표기 전용이라 건드리지 않고 여기서 따로 처리한다
export const renderCardText = (text: string) => {
  if (!text) return null;
  const parts = [];
  // 줄 첫머리의 #는 제목이다. 안 걸러내면 "# 제목"이 글자 그대로 찍힌다 (m 플래그로 줄마다 본다)
  const regex = /^#{1,6}[ \t]+([^\n]+)|\*\*([^*]+)\*\*|==([^=]+)==|`([^`\n]+)`/gm;
  let last = 0;
  let m;
  while ((m = regex.exec(text)) !== null) {
    if (m.index > last) parts.push(text.slice(last, m.index));
    if (m[1]) parts.push(<b key={m.index} className="md-h">{m[1]}</b>);             // # 제목
    else if (m[2]) parts.push(<b key={m.index}>{m[2]}</b>);                         // **굵게**
    else if (m[3]) parts.push(<mark key={m.index} className="hl">{m[3]}</mark>);    // ==형광==
    else parts.push(<code key={m.index} className="inline-code">{m[4]}</code>);     // `코드`
    last = regex.lastIndex;
  }
  if (last < text.length) parts.push(text.slice(last));
  return parts;
};

// 퀴즈 문제·해설 렌더러. ```코드펜스```는 코드블록으로, 나머지는 줄바꿈을 살려 문단으로 그린다.
// AI가 코드를 문장에 뭉개 넣으면 읽히지 않아서 여기서 떼어낸다
export const renderQuestion = (text: string) => {
  if (!text) return null;
  const blocks = [];
  const fence = /```[\w-]*\n?([\s\S]*?)```/g;
  let last = 0;
  let m;
  while ((m = fence.exec(text)) !== null) {
    if (m.index > last) blocks.push(<p key={last} className="q-text">{renderCardText(text.slice(last, m.index).trim())}</p>);
    blocks.push(<pre key={m.index} className="codebox">{m[1].replace(/\s+$/, '')}</pre>);
    last = fence.lastIndex;
  }
  if (last < text.length) blocks.push(<p key={last + 1} className="q-text">{renderCardText(text.slice(last).trim())}</p>);
  return blocks;
};
