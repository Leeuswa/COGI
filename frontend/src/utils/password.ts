// 비밀번호 규칙: 영어 + 숫자 + 특수문자 포함, 8자 이상
export const PW_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;
export const PW_HINT = '영어·숫자·특수문자를 포함해 8자 이상이어야 해요.';
export const isValidPassword = (pw: string) => PW_RULE.test(pw);
