package cms.gov.madie.measure.config;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@Endpoint(id = "cache-inspection")
@RequiredArgsConstructor
public class CacheInspectionEndpoint {

  private final CacheManager cacheManager;

  @ReadOperation
  public Map<String, Object> inspect() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("cacheManagerType", cacheManager.getClass().getSimpleName());
    MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
    result.put(
        "jvmMemory",
        Map.of(
            "heapUsedBytes", heap.getUsed(),
            "heapMaxBytes", heap.getMax(),
            "heapCommittedBytes", heap.getCommitted()));

    for (String cacheName : cacheManager.getCacheNames()) {
      Cache springCache = cacheManager.getCache(cacheName);
      if (springCache == null) {
        result.put(cacheName, Map.of("type", "N/A", "message", "Cache not found"));
      } else if (springCache instanceof CaffeineCache caffeineCache) {
        com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache =
            caffeineCache.getNativeCache();
        CacheStats stats = nativeCache.stats();
        Map<String, Object> cacheInfo = new LinkedHashMap<>();
        cacheInfo.put("type", "CaffeineCache");
        cacheInfo.put("entryCount", nativeCache.estimatedSize());
        cacheInfo.put("statsEnabled", nativeCache.policy().isRecordingStats());
        cacheInfo.put("hitCount", stats.hitCount());
        cacheInfo.put("missCount", stats.missCount());
        cacheInfo.put(
            "hitRate",
            stats.requestCount() > 0
                ? String.format(Locale.ROOT, "%.2f%%", stats.hitRate() * 100)
                : "N/A");
        cacheInfo.put("evictionCount", stats.evictionCount());
        cacheInfo.put("loadCount", stats.loadCount());
        cacheInfo.put(
            "averageLoadPenaltyMs",
            String.format(Locale.ROOT, "%.2f", stats.averageLoadPenalty() / 1_000_000.0));
        result.put(cacheName, cacheInfo);
      } else {
        result.put(
            cacheName,
            Map.of(
                "type",
                springCache.getClass().getSimpleName(),
                "message",
                "Cache statistics are only available for Caffeine caches"));
      }
    }
    return result;
  }
}
