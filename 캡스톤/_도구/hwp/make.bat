@echo off
rem 중간보고서 hwp 다시 만들기 (content.py 고친 다음 실행)
python build.py || exit /b 1
node pack.js 양식9-1_원본.hwp 결과.hwp Section0.bin DocInfo.bin bins.json
echo 결과.hwp 생성됨
