package com.phoenix.data.config;

import com.phoenix.data.properties.FileStorageProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.resource.PathResourceResolver;
import reactor.core.publisher.Mono;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Web配置类 (WebFlux 版本)
 */
@Slf4j
@Configuration
@AllArgsConstructor
public class WebConfig implements WebFluxConfigurer {

	/**
	 * 禁止经裸静态面直出的路径前缀（相对上传根目录，小写、不含首斜杠）。
	 *
	 * <p>R-01 / BUG-108（2026-10-06 实测）：静态资源处理器**不经过 sa-token 放行判定**，原先 `urlPrefix + "/**"`
	 * 把整棵上传目录对外裸暴露——实测**无需登录**即可下载知识库原件（`data-agent/agent-knowledge/&lt;uuid&gt;.md`，155,827 字节正文）
	 * 与会话工作区文件（`agent-workspace/agent-XX/&lt;sessionId&gt;/*`）。本清单把这两类敏感前缀从静态面摘除
	 * （返回 404、响应体不含任何正文），其余既有用途（头像等图片、通用上传回显）保持原样 ⇒ 符合 R-08「不得只删不补」。
	 *
	 * <p>知识库原件的**受控**访问改走受鉴权接口（`GET /api/agent-knowledge/{id}/raw`：校验登录 + 归属/绑定 + 审计）。
	 */
	private static final List<String> DENIED_STATIC_PREFIXES = List.of("data-agent/agent-knowledge", "agent-workspace");

	/** 静态资源缓存时长（保持原行为：1 小时）。 */
	private static final Duration STATIC_CACHE_TTL = Duration.ofHours(1);

	private final FileStorageProperties fileStorageProperties;

	/**
	 * 配置静态资源处理器，将文件存储路径映射为 URL 访问路径并设置缓存策略。
	 * @param registry 资源处理器注册表
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String uploadDir = Paths.get(fileStorageProperties.getPath()).toAbsolutePath().toString();

		registry.addResourceHandler(fileStorageProperties.getUrlPrefix() + "/**")
			.addResourceLocations("file:" + uploadDir + "/")
			.setCacheControl(CacheControl.maxAge(STATIC_CACHE_TTL))
			.resourceChain(true)
			.addResolver(new SensitivePathDenyingResolver());
	}

	/**
	 * 敏感路径拒绝器：命中 {@link #DENIED_STATIC_PREFIXES} 时返回 {@code null}（表现为 404，且响应体不含正文）。
	 *
	 * <p>比对前先归一化（去首斜杠、`\`→`/`、折叠 `.`/`..`），避免用反斜杠、重复斜杠或点段变形绕过前缀比对
	 * （本次已实测 `%2e%2e`/双编码/反斜杠在 nginx 层被挡；这里是共享面自身的第二道防线）。
	 */
	private static final class SensitivePathDenyingResolver extends PathResourceResolver {

		@Override
		protected Mono<Resource> getResource(String resourcePath, Resource location) {
			if (isDenied(resourcePath)) {
				log.warn("静态面拒绝敏感路径（R-01/BUG-108）: path={}", resourcePath);
				return Mono.empty();
			}
			return super.getResource(resourcePath, location);
		}

		private static boolean isDenied(String resourcePath) {
			if (resourcePath == null || resourcePath.isEmpty()) {
				return false;
			}
			String normalized = normalize(resourcePath);
			for (String denied : DENIED_STATIC_PREFIXES) {
				if (normalized.equals(denied) || normalized.startsWith(denied + "/")) {
					return true;
				}
			}
			return false;
		}

		private static String normalize(String resourcePath) {
			String unified = resourcePath.replace('\\', '/');
			while (unified.startsWith("/")) {
				unified = unified.substring(1);
			}
			// Path.normalize 折叠 ./ 与 ../（若 `..` 逃出根，归一化结果仍以 .. 开头 ⇒ 与前缀不匹配；
			// 根逃逸另由 PathResourceResolver 自身防护，此处只保证前缀比对不被变形绕过）
			String collapsed = Paths.get(unified).normalize().toString().replace('\\', '/');
			return collapsed.toLowerCase(Locale.ROOT);
		}
	}

}
