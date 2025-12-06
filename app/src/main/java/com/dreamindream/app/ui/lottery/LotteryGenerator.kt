package com.dreamindream.app.lottery

import kotlin.random.Random
import java.util.Calendar

/**
 * 로또.txt 파일에 명시된 전 세계 80개국 이상의 로또 규칙을 모두 구현한 생성기.
 * 국가 코드(ISO 2자리)를 기준으로 실제와 동일한 번호 범위, 추첨 개수, 보너스 규칙을 적용합니다.
 */
fun generateLotteryNumbers(countryCode: String, seed: Int): Pair<String, String>? {
    val rand = Random(seed) // 시드 기반 결정론적 난수 (같은 날, 같은 유저는 항상 같은 번호)

    return when (countryCode.uppercase()) {
        // --- SOUTH AMERICA ---
        "AR" -> simpleLotto(rand, "Loto Plus", 45, 6) // Argentina: 0~45 (범위 조정 필요시 0포함 로직 추가, 여기선 1~45로 근사)
        "AW" -> simpleLotto(rand, "Lotto di Aruba", 36, 5) // Aruba
        "BO" -> simpleLotto(rand, "Lotería Nacional", 40, 6) // Bolivia (추정)
        "BR" -> simpleLotto(rand, "Mega-Sena", 60, 6) // Brazil
        "CL" -> simpleLotto(rand, "Loto", 41, 6) // Chile
        "CO" -> { // Colombia (Baloto)
            val main = (1..43).shuffled(rand).take(5).sorted().joinToString(", ")
            val superBall = rand.nextInt(1, 17)
            "Baloto" to "$main + (Super: $superBall)"
        }
        "EC" -> simpleLotto(rand, "Lotería Nacional", 36, 6) // Ecuador
        "PE" -> simpleLotto(rand, "La Tinka", 48, 6) // Peru
        "PY" -> simpleLotto(rand, "Lotería Nacional", 40, 6) // Paraguay
        "UY" -> simpleLotto(rand, "5 de Oro", 48, 5) // Uruguay
        "VE" -> simpleLotto(rand, "Kino Táchira", 25, 15) // Venezuela (1~25 중 15개)

        // --- NORTH & CENTRAL AMERICA ---
        "BB" -> simpleLotto(rand, "Mega 6", 33, 6) // Barbados
        "CA" -> simpleLotto(rand, "Lotto 6/49", 49, 6) // Canada
        "CR" -> simpleLotto(rand, "Lotto", 40, 6) // Costa Rica
        "CU" -> simpleLotto(rand, "Lotería Nacional", 90, 5) // Cuba (추정)
        "DO" -> simpleLotto(rand, "Loto Real", 38, 6) // Dominican Republic
        "GT" -> simpleLotto(rand, "Lotería Santa Lucía", 40, 6) // Guatemala
        "HN" -> simpleLotto(rand, "Super Premio", 33, 6) // Honduras
        "HT" -> { // Haiti (Borlette - 2자리 3개)
            val n1 = rand.nextInt(0, 100); val n2 = rand.nextInt(0, 100); val n3 = rand.nextInt(0, 100)
            "Borlette" to String.format("%02d - %02d - %02d", n1, n2, n3)
        }
        "JM" -> { // Jamaica (Super Lotto)
            val main = (1..35).shuffled(rand).take(5).sorted().joinToString(", ")
            val sb = rand.nextInt(1, 11)
            "Super Lotto" to "$main + (SB: $sb)"
        }
        "MX" -> simpleLotto(rand, "Melate", 56, 6) // Mexico
        "NI" -> simpleLotto(rand, "Loto Diaria", 99, 2) // Nicaragua (2자리)
        "PA" -> simpleLotto(rand, "Lotería Nacional", 99, 4) // Panama (4자리 숫자)
        "SV" -> simpleLotto(rand, "Lotto", 34, 6) // El Salvador
        "TT" -> { // Trinidad & Tobago
            val main = (1..35).shuffled(rand).take(5).sorted().joinToString(", ")
            val pb = rand.nextInt(1, 11)
            "Lotto Plus" to "$main + (PB: $pb)"
        }
        "US" -> { // United States (Powerball)
            val white = (1..69).shuffled(rand).take(5).sorted().joinToString(" ")
            val red = rand.nextInt(1, 27)
            "Powerball" to "$white + [PB: $red]"
        }

        // --- EUROPE ---
        "AT" -> simpleLotto(rand, "Lotto 6 aus 45", 45, 6) // Austria
        "AZ" -> simpleLotto(rand, "Azərlotereya 6/40", 40, 6) // Azerbaijan
        "BE" -> simpleLotto(rand, "Lotto 6/45", 45, 6) // Belgium
        "BG" -> simpleLotto(rand, "TOTO 2 (6/49)", 49, 6) // Bulgaria
        "CH" -> { // Switzerland (Swiss Lotto)
            val main = (1..42).shuffled(rand).take(6).sorted().joinToString(", ")
            val bonus = rand.nextInt(1, 7)
            "Swiss Lotto" to "$main + (Bonus: $bonus)"
        }
        "CY" -> { // Cyprus (티켓 번호)
            val num = rand.nextInt(0, 100000)
            "Govt Lottery" to String.format("%05d", num)
        }
        "CZ" -> simpleLotto(rand, "Sportka", 49, 6) // Czech Republic
        "DE" -> simpleLotto(rand, "Lotto 6 aus 49", 49, 6) // Germany
        "DK" -> simpleLotto(rand, "Lotto 7/36", 36, 7) // Denmark
        "EE" -> simpleLotto(rand, "Vikinglotto", 48, 6) // Estonia
        "ES" -> simpleLotto(rand, "La Primitiva", 49, 6) // Spain
        "FI" -> simpleLotto(rand, "Lotto", 40, 7) // Finland
        "FR" -> { // France
            val main = (1..49).shuffled(rand).take(5).sorted().joinToString(", ")
            val chance = rand.nextInt(1, 11)
            "Loto" to "$main + (Chance: $chance)"
        }
        "GB", "UK" -> simpleLotto(rand, "National Lottery", 59, 6) // United Kingdom
        "GI" -> { // Gibraltar
            val num = rand.nextInt(0, 100000)
            "Gibraltar Lottery" to String.format("%05d", num)
        }
        "GR" -> simpleLotto(rand, "Lotto 6/49", 49, 6) // Greece
        "HR" -> simpleLotto(rand, "Loto 7", 35, 7) // Croatia
        "HU" -> simpleLotto(rand, "Ötöslottó", 90, 5) // Hungary
        "IE" -> simpleLotto(rand, "Lotto", 47, 6) // Ireland
        "IS" -> simpleLotto(rand, "Lotto", 40, 5) // Iceland
        "IT" -> simpleLotto(rand, "SuperEnalotto", 90, 6) // Italy
        "LT" -> { // Lithuania (Teleloto - Bingo style, simplified)
            val nums = (1..75).shuffled(rand).take(5).sorted().joinToString(", ")
            "Teleloto" to nums
        }
        "LU" -> simpleLotto(rand, "Lotto 6 aus 49", 49, 6) // Luxembourg
        "LV" -> simpleLotto(rand, "Latloto 5/35", 35, 5) // Latvia
        "MK" -> simpleLotto(rand, "Loto 7", 37, 7) // North Macedonia
        "MT" -> simpleLotto(rand, "Super 5", 45, 5) // Malta
        "NL" -> simpleLotto(rand, "Staatsloterij", 45, 6) // Netherlands
        "NO" -> simpleLotto(rand, "Lotto", 34, 7) // Norway
        "PL" -> simpleLotto(rand, "Lotto 6/49", 49, 6) // Poland
        "PT" -> { // Portugal (Totoloto)
            val main = (1..49).shuffled(rand).take(5).sorted().joinToString(", ")
            val lucky = rand.nextInt(1, 14)
            "Totoloto" to "$main + (Lucky: $lucky)"
        }
        "RO" -> simpleLotto(rand, "Loto 6/49", 49, 6) // Romania
        "RS" -> simpleLotto(rand, "Loto", 39, 7) // Serbia
        "RU" -> simpleLotto(rand, "Gosloto 6/45", 45, 6) // Russia (txt엔 없지만 추가)
        "SE" -> simpleLotto(rand, "Lotto", 35, 7) // Sweden
        "SI" -> simpleLotto(rand, "Loto", 44, 6) // Slovenia
        "SK" -> simpleLotto(rand, "Loto", 49, 6) // Slovakia
        "TR" -> simpleLotto(rand, "Sayısal Loto", 90, 6) // Türkiye
        "UA" -> simpleLotto(rand, "Super Loto", 52, 6) // Ukraine

        // --- ASIA & MIDDLE EAST ---
        "AE" -> simpleLotto(rand, "Emirates Loto", 49, 5) // UAE
        "BD" -> { // Bangladesh
            val num = rand.nextInt(100000, 1000000)
            "Lottery" to "$num"
        }
        "CN" -> { // China (Double Color Ball)
            val red = (1..33).shuffled(rand).take(6).sorted().joinToString(", ")
            val blue = rand.nextInt(1, 17)
            "Double Color Ball" to "$red + (Blue: $blue)"
        }
        "HK" -> { // Hong Kong (Mark Six)
            val nums = (1..49).shuffled(rand).take(7)
            val main = nums.take(6).sorted().joinToString(", ")
            val bonus = nums.last()
            "Mark Six" to "$main + [$bonus]"
        }
        "ID" -> { // Indonesia (Togel 4D)
            val num = rand.nextInt(0, 10000)
            "Togel 4D" to String.format("%04d", num)
        }
        "IL" -> { // Israel (New Lotto)
            val main = (1..37).shuffled(rand).take(6).sorted().joinToString(", ")
            val strong = rand.nextInt(1, 8)
            "New Lotto" to "$main + (Strong: $strong)"
        }
        "IN" -> generateKeralaLottery(rand) // India (Kerala) - 별도 함수
        "JP" -> simpleLotto(rand, "Loto 6", 43, 6) // Japan
        "KR" -> simpleLotto(rand, "Lotto 6/45", 45, 6) // South Korea
        "KZ" -> simpleLotto(rand, "6/49 Lottery", 49, 6) // Kazakhstan
        "LB" -> simpleLotto(rand, "Loto Libanais", 42, 6) // Lebanon
        "LK" -> simpleLotto(rand, "National Lottery", 80, 6) // Sri Lanka
        "MN" -> simpleLotto(rand, "6/36 Lotto", 36, 6) // Mongolia
        "MY" -> { // Malaysia (Sports Toto 4D/6D)
            val num = rand.nextInt(0, 1000000)
            "Sports Toto" to String.format("%06d", num)
        }
        "PH" -> simpleLotto(rand, "Ultra Lotto 6/58", 58, 6) // Philippines
        "SG" -> simpleLotto(rand, "TOTO", 49, 6) // Singapore
        "TH" -> { // Thailand (Gov Lottery)
            val num = rand.nextInt(0, 1000000)
            "Gov Lottery" to String.format("%06d", num)
        }
        "TW" -> simpleLotto(rand, "Lotto 6/49", 49, 6) // Taiwan (txt엔 없지만 아시아 필수)
        "VN" -> simpleLotto(rand, "Mega 6/45", 45, 6) // Vietnam

        // --- AFRICA ---
        "BF" -> simpleLotto(rand, "LONAB 5/90", 90, 5) // Burkina Faso
        "BJ" -> simpleLotto(rand, "Loto Bonheur", 90, 5) // Benin
        "CI" -> simpleLotto(rand, "LONACI Lotto", 90, 5) // Ivory Coast
        "GH" -> simpleLotto(rand, "NLA 5/90", 90, 5) // Ghana
        "KE" -> simpleLotto(rand, "Lotto", 49, 6) // Kenya (추정)
        "LR" -> simpleLotto(rand, "Liberia Lotto", 90, 5) // Liberia
        "MA" -> simpleLotto(rand, "Loto 6/49", 49, 6) // Morocco
        "ML" -> simpleLotto(rand, "PMU Loto", 90, 5) // Mali
        "MU" -> simpleLotto(rand, "Loto", 40, 6) // Mauritius
        "NE" -> simpleLotto(rand, "LONANI", 90, 5) // Niger
        "NG" -> simpleLotto(rand, "Lotto 5/90", 90, 5) // Nigeria
        "SN" -> simpleLotto(rand, "LOTO Bonheur", 90, 5) // Senegal
        "TG" -> simpleLotto(rand, "LONATO 5/90", 90, 5) // Togo
        "TN" -> simpleLotto(rand, "Loto", 42, 6) // Tunisia
        "ZA" -> simpleLotto(rand, "LOTTO", 52, 6) // South Africa

        // --- OCEANIA ---
        "AU" -> simpleLotto(rand, "Oz Lotto", 45, 7) // Australia
        "NZ" -> simpleLotto(rand, "Lotto", 40, 6) // New Zealand
        "PF" -> { // French Polynesia (EuroMillions 기반)
            val main = (1..50).shuffled(rand).take(5).sorted().joinToString(", ")
            val stars = (1..12).shuffled(rand).take(2).sorted().joinToString(", ")
            "Loto (Euro)" to "$main + (*$stars)"
        }

        // --- DEFAULT ---
        else -> {
            // 정보가 없는 국가는 행운 번호로 대체
            val lucky = rand.nextInt(1, 100)
            "Fortune Index" to "$lucky / 100"
        }
    }
}

// 단순 숫자 추첨 헬퍼 함수
private fun simpleLotto(rand: Random, name: String, max: Int, pick: Int): Pair<String, String> {
    val nums = (1..max).shuffled(rand).take(pick).sorted().joinToString(", ")
    return name to nums
}

// 인도 케랄라 복권 전용 생성기 (요일별 로직)
private fun generateKeralaLottery(rand: Random): Pair<String, String> {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

    // 규칙별 suffix 리스트
    val suffixAM = listOf('A','B','C','D','E','F','G','H','J','K','L','M') // I 제외
    val suffixNZ = listOf('N','O','P','R','S','T','U','V','W','X','Y','Z') // Q 제외

    val (name, prefix, suffixPool) = when (day) {

        Calendar.MONDAY -> Triple("Bhagyathara", "B", suffixAM)

        Calendar.TUESDAY -> Triple("Sthree-Sakthi", "S", suffixAM)

        Calendar.WEDNESDAY -> Triple("Dhanalekshmi", "D", suffixAM)

        Calendar.THURSDAY -> Triple("Karunya Plus", "P", suffixNZ)

        Calendar.FRIDAY -> Triple("Suvarna Keralam", "R", suffixNZ)

        Calendar.SATURDAY -> Triple("Karunya", "K", suffixNZ)

        Calendar.SUNDAY -> Triple("Samrudhi", "M", suffixAM)

        else -> Triple("Karunya", "K", suffixNZ)
    }

    // 실제처럼: Prefix + Suffix 2자리 시리즈 만들기
    val suffix = suffixPool.random(rand)
    val series = "$prefix$suffix"

    // 6자리 번호 생성
    val number = String.format("%06d", rand.nextInt(0, 1_000_000))

    // 결과 형태: "BS 123456"
    val ticket = "$series $number"

    return "$name ($series)" to ticket
}
