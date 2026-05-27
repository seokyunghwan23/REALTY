const https = require('https');
const os = require('os');

// 공인 IP 조회
function getPublicIP() {
  return new Promise((resolve, reject) => {
    https.get('https://api.ipify.org?format=json', (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          resolve(json.ip);
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', reject);
  });
}

// 로컬 IP 조회
function getLocalIP() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name]) {
      // IPv4이고 내부 주소가 아닌 것
      if (iface.family === 'IPv4' && !iface.internal) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

async function main() {
  try {
    const publicIP = await getPublicIP();
    const localIP = getLocalIP();

    // 보안 토큰
    const SECRET_TOKEN = "RealtyMate_SecureToken_2025_KH_System";

    //console.log('\n' + '='.repeat(70));
    //console.log('부동산 매물 검색 시스템 - 접속 정보');
    //console.log('='.repeat(70));

    //console.log('\n[로컬 접속 (같은 PC)]');
    //console.log(`  프론트엔드: http://localhost:3000?token=${SECRET_TOKEN}`);
    //console.log(`  백엔드 API: http://localhost:8081`);

    //console.log('\n[같은 WiFi 네트워크에서 접속 (같은 공유기)]');
    //console.log(`  프론트엔드: http://${localIP}:3000?token=${SECRET_TOKEN}`);
    //console.log(`  백엔드 API: http://${localIP}:8081`);

    //console.log('\n[외부에서 접속 (인터넷을 통해)]');
    //console.log(`  공인 IP: ${publicIP}`);
    //console.log(`  프론트엔드: http://${publicIP}:3000?token=${SECRET_TOKEN}`);
    //console.log(`  백엔드 API: http://${publicIP}:8081`);

    //console.log('\n[필요한 설정]');
    //console.log('  1. Windows 방화벽에서 포트 3000, 8081 허용');
    //console.log('  2. 공유기 사용 시: 포트포워딩 설정 필요');
    //console.log('     - 외부 포트 3000 → 내부 ' + localIP + ':3000');
    //console.log('     - 외부 포트 8081 → 내부 ' + localIP + ':8081');
    //console.log('  3. Spring Boot 서버 시작 (포트 8081)');
    //console.log('  4. React 개발 서버 시작 (포트 3000)');

    //console.log('\n' + '='.repeat(70) + '\n');
  } catch (error) {
    console.error('공인 IP 조회 실패:', error.message);
  }
}

main();
