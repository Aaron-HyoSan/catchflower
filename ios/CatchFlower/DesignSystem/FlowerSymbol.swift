import SwiftUI

/// 꽃 일러스트 **자리 채우기** 심볼.
///
/// **왜 필요한가.** 일러스트 200종(C 발주서)은 아직 발주 전이다. 그런데
/// `도감 그리드가 곧 일러스트 그 자체`(기획서 6장)라서, 그림이 없으면
/// 화면 04·05·22를 검증할 수가 없다 — 빈 사각형 200개만 보게 된다.
///
/// 그래서 CSV가 이미 가진 값(대표색 · 과 · 도감번호)으로 **종마다 다르게 보이는**
/// 도형을 만든다. 예쁘게 만드는 게 목적이 아니라 **200종이 서로 구별되는지,
/// 32pt에서 형태가 남는지**를 지금 확인하는 게 목적이다 (발주서 1절의 최우선 요구사항).
///
/// **SVG 200종이 오면 이 뷰의 내부만 교체한다.** 호출부는 바뀌지 않는다.
struct FlowerSymbol: View {
    let flower: Flower
    /// 미발견 셀은 실루엣으로 그린다 (화면 04). B-3 추가 요청의 (b)안.
    var isDiscovered: Bool = true

    private var baseColor: Color {
        Theme.Palette.flowerColor(flower.color)
    }

    /// 꽃잎 수를 과(科)로 가른다. 실제 형태와 완전히 일치할 필요는 없고,
    /// **같은 과는 비슷하게 · 다른 과는 다르게** 보이면 목적을 달성한다.
    private var petalCount: Int {
        switch flower.family {
        case "국화과": return 12
        case "장미과": return 5
        case "십자화과": return 4
        case "백합과", "붓꽃과": return 6
        case "콩과": return 3
        case "진달래과", "메꽃과": return 5
        case "미나리아재비과": return 5
        case "석죽과": return 5
        case "꿀풀과", "현삼과": return 4
        case "물푸레나무과": return 4
        default:
            // 그 외는 도감번호로 흩어 준다 (4~8장). 인접 번호가 뭉치지 않게 소수를 곱한다.
            return 4 + (flower.id * 7) % 5
        }
    }

    /// 꽃잎 길쭉함. 같은 과·같은 색이어도 종이 구별되게 한다.
    private var petalRatio: CGFloat {
        0.30 + CGFloat((flower.id * 13) % 5) * 0.055
    }

    /// 꽃 중심부(암술·수술) 비율.
    private var coreRatio: CGFloat {
        flower.family == "국화과" ? 0.34 : 0.22
    }

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            let petalLength = side * 0.42
            let petalWidth = petalLength * petalRatio

            ZStack {
                ForEach(0..<petalCount, id: \.self) { i in
                    Capsule()
                        .fill(petalFill)
                        // 흰 꽃 46종이 흰 배경에서 사라지는 문제 — 발주서에도 있는 요구사항.
                        .overlay(Capsule().stroke(outlineColor, lineWidth: side * 0.012))
                        .frame(width: petalWidth, height: petalLength)
                        .offset(y: -petalLength / 2)
                        .rotationEffect(.degrees(Double(i) / Double(petalCount) * 360))
                        .position(center)
                }

                Circle()
                    .fill(coreFill)
                    .frame(width: side * coreRatio, height: side * coreRatio)
                    .position(center)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }

    private var petalFill: Color {
        isDiscovered ? baseColor : Theme.Palette.surfaceAlt
    }

    /// 외곽선. **흰 꽃 46종이 흰 배경에서 사라지는 문제**를 막는 게 유일한 목적이다.
    /// 꽃잎 색을 어둡게 깐 것 위에 꽃잎을 올려 명도차를 만든다.
    private var outlineColor: Color {
        isDiscovered ? baseColor.darkened(0.35) : Theme.Palette.border
    }

    private var coreFill: Color {
        isDiscovered
            ? Color(red: 0.98, green: 0.85, blue: 0.42)
            : Theme.Palette.border.opacity(0.7)
    }
}

private extension Color {
    /// HSB의 명도만 낮춘다. 색상(hue)은 유지해야 꽃 색이 유지된다.
    func darkened(_ amount: Double) -> Color {
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getHue(&h, saturation: &s, brightness: &b, alpha: &a)
        return Color(
            hue: Double(h),
            saturation: Double(min(s + amount * 0.3, 1)),
            brightness: Double(max(b - amount, 0)),
            opacity: Double(a)
        )
    }
}

/// 심볼 검수 화면. **발주 전에 여기서 판단한다**:
/// 32pt에서 종이 구별되는가, 흰 꽃이 배경에 묻지 않는가.
private struct FlowerSymbolGallery: View {
    let flowers: [Flower]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text("도감 그리드 크기 (88pt)")
                    .font(Theme.Typo.sectionTitle)
                LazyVGrid(columns: Array(repeating: GridItem(), count: 3), spacing: 12) {
                    ForEach(flowers.prefix(12)) { f in
                        VStack(spacing: 4) {
                            FlowerSymbol(flower: f).frame(width: 88, height: 88)
                            Text(f.name).font(Theme.Typo.caption)
                        }
                    }
                }

                Text("랭킹 대표 꽃 크기 (32pt) — 이 크기에서 구별돼야 한다")
                    .font(Theme.Typo.sectionTitle)
                HStack(spacing: 8) {
                    ForEach(flowers.prefix(10)) { f in
                        FlowerSymbol(flower: f).frame(width: 32, height: 32)
                    }
                }

                Text("흰 꽃 — 흰 배경에서 사라지지 않는지 확인")
                    .font(Theme.Typo.sectionTitle)
                HStack(spacing: 8) {
                    ForEach(flowers.filter { $0.color == "흰색" }.prefix(6)) { f in
                        FlowerSymbol(flower: f).frame(width: 64, height: 64)
                    }
                }
                .padding(8)
                .background(Color.white)

                Text("미발견 실루엣")
                    .font(Theme.Typo.sectionTitle)
                HStack(spacing: 8) {
                    ForEach(flowers.prefix(5)) { f in
                        FlowerSymbol(flower: f, isDiscovered: false)
                            .frame(width: 72, height: 72)
                    }
                }
            }
            .padding(Theme.Metric.screenPadding)
        }
        .background(Theme.Palette.background)
    }
}

#Preview("심볼 검수") {
    FlowerSymbolGallery(flowers: FlowerRepository().flowers)
}
