// 정산 결과 알림 메일. Apps Script 웹 앱으로 배포한다 (docs/automation.md).
// 스크립트 속성: TOKEN(주소 뒤 ?token= 값과 같아야 함), MAIL_TO(받을 메일 주소)
function doPost(e) {
  var props = PropertiesService.getScriptProperties();
  if (!e.parameter.token || e.parameter.token !== props.getProperty('TOKEN')) {
    return ContentService.createTextOutput('forbidden');
  }
  var r = JSON.parse(e.postData.contents);
  var bad = r.inconsistentIds || [];
  var missing = r.missing || [];
  var subject = (r.ok ? '[정산 완료] ' : '[정산 확인 필요] ') + r.date;
  var body = [
    '정산 날짜: ' + r.date,
    '새로 만든 정산: ' + r.created + '건' + (r.skipped ? ' (다른 서버가 실행 중이라 건너뜀)' : ''),
    '정합성 오류: ' + (bad.length ? bad.join(', ') : '없음'),
    '정산 누락(최근 7일): ' + (missing.length
      ? missing.map(function (m) { return m.date + ' 판매자 ' + m.sellerId; }).join(', ')
      : '없음')
  ].join('\n');
  MailApp.sendEmail(props.getProperty('MAIL_TO'), subject, body);
  return ContentService.createTextOutput('ok');
}
