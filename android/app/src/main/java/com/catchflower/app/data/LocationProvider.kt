package com.catchflower.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/** 촬영 좌표. 위치 권한이 없거나 실패하면 null이다. */
data class Coordinate(val lat: Double, val lng: Double)

/**
 * 촬영 위치를 얻는다.
 *
 * 인터페이스로 둔 이유: **테스트와 에뮬레이터에서 위치가 안 나온다.** 구현체를
 * 갈아 끼우지 못하면 저장 경로 전체가 "좌표 null"로만 검증된다.
 */
interface LocationSource {
    suspend fun current(): Coordinate?
}

/** 위치를 쓰지 않는다. 권한 거부·테스트용. */
object NoLocationSource : LocationSource {
    override suspend fun current(): Coordinate? = null
}

/**
 * 플랫폼 [LocationManager] 구현.
 *
 * **왜 Play Services(`FusedLocationProviderClient`)가 아닌가.** 이 앱은 이미
 * ML Kit로 Play Services에 묶여 있지만, 위치는 **한 번의 대략적 좌표**만 필요하다
 * (동 단위 랭킹 + 100m 반경 B-5). 정밀 추적이 필요 없어서 플랫폼 API로 충분하고,
 * 의존성이 하나 줄면 Play Services 없는 기기(중국 롬 등)에서도 장소가 붙는다.
 *
 * ⚠️ **권한을 여기서 요청하지 않는다.** 요청은 화면이 한다 —
 *    `허용하지 않아도 도감은 쓸 수 있지만 일부 기능이 제한돼요`(화면 03)가 약속이라
 *    거부한 사용자에게 촬영마다 다시 물으면 안내가 아니라 강요가 된다.
 *    여기서는 권한이 없으면 조용히 null을 준다.
 */
class PlatformLocationSource(private val context: Context) : LocationSource {

    override suspend fun current(): Coordinate? {
        if (!hasPermission(context)) return null
        val manager = ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: return null

        // ① 마지막으로 알려진 위치. 즉시 오고 대부분 충분하다 —
        //    꽃 사진을 찍는 사람은 방금 그 자리에 있었다.
        lastKnown(manager)?.let { return it }

        // ② 없으면 한 번 갱신을 기다린다. **타임아웃을 반드시 둔다** —
        //    실내나 GPS 꺼진 상태에서 안 주면 촬영 흐름이 영원히 멈춘다.
        //    등록은 위치 없이도 되어야 하므로 시간 안에 못 오면 포기한다.
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { requestOnce(manager) }
    }

    @Suppress("MissingPermission") // 위에서 확인했다
    private fun lastKnown(manager: LocationManager): Coordinate? {
        // 정확도 좋은 순서가 아니라 **최근 순서**로 고른다. 오래된 GPS 좌표보다
        // 방금 잡힌 네트워크 좌표가 "지금 여기"에 가깝다.
        val candidates = PROVIDERS.mapNotNull { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider)
                else null
            }.getOrNull()
        }
        val best = candidates.maxByOrNull { it.time } ?: return null
        // 너무 오래된 좌표는 다른 동네일 수 있다. 동 단위 랭킹이 걸려 있어 버린다.
        if (System.currentTimeMillis() - best.time > MAX_AGE_MS) return null
        return best.toCoordinate()
    }

    @Suppress("MissingPermission")
    private suspend fun requestOnce(manager: LocationManager): Coordinate? =
        suspendCancellableCoroutine { cont ->
            val provider = PROVIDERS.firstOrNull {
                runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
            }
            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            // ⚠️ `requestSingleUpdate`를 쓰지 않는다 — API 30에서 deprecated이고
            //    "한 번만 받는다"를 프레임워크가 보장해 주지 않는 기기가 있다.
            //    `requestLocationUpdates` + 첫 좌표에서 직접 `removeUpdates`가 확실하다.
            lateinit var listener: android.location.LocationListener
            listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    // **먼저 뗀다.** 안 떼면 좌표가 계속 들어오고, 두 번째 호출에서
                    // 이미 resume된 continuation을 또 resume해 IllegalStateException이 난다.
                    runCatching { manager.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(location.toCoordinate())
                }

                // API 26 대응. 없으면 일부 기기에서 AbstractMethodError로 죽는다.
                @Deprecated("API 29에서 제거됐지만 minSdk 26을 지원해야 한다")
                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit

                override fun onProviderDisabled(p: String) {
                    runCatching { manager.removeUpdates(listener) }
                    if (cont.isActive) cont.resume(null)
                }
            }
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    /* minTimeMs = */ 0L,
                    /* minDistanceM = */ 0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure {
                if (cont.isActive) cont.resume(null)
                return@suspendCancellableCoroutine
            }
            // 타임아웃·취소 때 리스너를 반드시 뗀다. 안 떼면 화면을 나가도 GPS가 계속 돈다.
            cont.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        }

    private fun Location.toCoordinate() = Coordinate(latitude, longitude)

    companion object {
        /**
         * ⚠️ **정확한 위치와 대략적 위치를 둘 다 본다.** `ACCESS_FINE_LOCATION`만 보면
         *    사용자가 "대략적 위치"를 고른 경우(API 31+ 기본 선택지) 권한이 있는데도
         *    장소가 영구히 안 붙는다. 동 단위·100m면 대략적 위치로 충분하다.
         */
        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        private val PROVIDERS = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )

        /** 5초. 이보다 길면 촬영 후 대기가 사용자에게 "멈춤"으로 읽힌다. */
        private const val REQUEST_TIMEOUT_MS = 5_000L

        /** 2분. 그보다 오래된 좌표는 다른 장소일 수 있다. */
        private const val MAX_AGE_MS = 2 * 60 * 1000L
    }
}
