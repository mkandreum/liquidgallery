package com.example.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language

@Language("AGSL")
val LIQUID_GLASS_SHADER = """
    uniform shader image;
    uniform float4 barRect;          // [left, top, right, bottom]
    uniform float4 densityBarRect;   // [left, top, right, bottom]
    uniform float4 searchBarRect;    // [left, top, right, bottom]
    uniform float4 searchFabRect;    // [left, top, right, bottom]
    uniform float4 selectBtnRect;     // [left, top, right, bottom]
    uniform float4 collapsedFabRect;  // [left, top, right, bottom]
    uniform float4 pageIndicatorRect; // [left, top, right, bottom]
    uniform float2 resolution;

    half4 applyGlassEffect(float2 coord, float4 rect, half4 baseColor) {
        float2 size = rect.zw - rect.xy;
        if (size.x <= 0.0 || size.y <= 0.0) return baseColor;

        float2 center = (rect.xy + rect.zw) * 0.5;
        float r = size.y * 0.5; // corner radius for capsule/pill shape

        // 1. Calculate Signed Distance Field (SDF) of the rounded pill shape
        float2 halfSize = size * 0.5;
        float2 pos = abs(coord - center);
        float2 q = pos - (halfSize - r);
        float sdf = length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;

        // Expand the boundary check to 1.5 pixels to cover anti-aliased Compose borders
        if (sdf > 1.5) return baseColor;

        // Calculate a smooth edge alpha (1.0 inside, fading to 0.0 at 1.5px outside)
        float edgeAlpha = 1.0 - smoothstep(0.0, 1.5, sdf);

        // 2. Define responsive bevel boundary (20px width) and calculate normalized distance inside it
        float w = min(r * 0.85, 20.0);
        float d = -sdf; // distance from edge (positive inside)
        float u = clamp(d / w, 0.0, 1.0); // 0.0 at outer edge, 1.0 at flat center

        // Curvature profile that goes to 0.0 at the flat center (u = 1.0)
        float normalTilt = pow(1.0 - u, 1.5);

        // 3. Surface normal pointing outward from the pill boundary
        float2 capCenterOffset = float2(max(0.0, halfSize.x - r), 0.0);
        float2 localCapCoord = coord - center;
        
        float2 dir;
        if (abs(localCapCoord.x) > capCenterOffset.x) {
            float2 capCenter = center + sign(localCapCoord.x) * capCenterOffset;
            float2 diff = coord - capCenter;
            float len = length(diff);
            dir = len > 0.001 ? diff / len : float2(sign(localCapCoord.x), 0.0);
        } else {
            dir = float2(0.0, localCapCoord.y >= 0.0 ? 1.0 : -1.0);
        }

        // Compute 3D surface normal: curves at the 20px bevel, flat N = (0,0,1) in the center
        float3 N = normalize(float3(dir * normalTilt * 2.5, 1.0));

        // 4. Snell's Law Refraction displacement (Inward direction for magnifying glass/lupa)
        float eta = 0.55; // High refractive index for magnifying bevel
        float k = 1.0 - eta * eta * (1.0 - N.z * N.z);
        float refractScale = sqrt(k) - eta * N.z;
        
        // Disable distortion completely outside the pill to keep edges perfectly clean
        float edgeDistortionScale = clamp(1.0 - sdf, 0.0, 1.0);
        float2 distortion = -dir * (refractScale * normalTilt * (r * 0.8) * edgeDistortionScale);
        
        float2 distortedCoord = coord + distortion;

        // 5. Chromatic aberration: split RGB slightly (reduced aberration)
        float2 rCoord = coord + distortion * 1.08;
        float2 bCoord = coord + distortion * 0.93;

        // 6. Blur and sample each channel
        float blurOff = 2.0;
        float2 c0 = clamp(distortedCoord, float2(0.0), resolution);
        float2 c1 = clamp(distortedCoord + float2(-blurOff, 0.0), float2(0.0), resolution);
        float2 c2 = clamp(distortedCoord + float2(blurOff, 0.0), float2(0.0), resolution);
        float2 c3 = clamp(distortedCoord + float2(0.0, -blurOff), float2(0.0), resolution);
        float2 c4 = clamp(distortedCoord + float2(0.0, blurOff), float2(0.0), resolution);

        float2 r0 = clamp(rCoord, float2(0.0), resolution);
        float2 r1 = clamp(rCoord + float2(-blurOff, 0.0), float2(0.0), resolution);
        float2 r2 = clamp(rCoord + float2(blurOff, 0.0), float2(0.0), resolution);
        float2 r3 = clamp(rCoord + float2(0.0, -blurOff), float2(0.0), resolution);
        float2 r4 = clamp(rCoord + float2(0.0, blurOff), float2(0.0), resolution);

        float2 b0 = clamp(bCoord, float2(0.0), resolution);
        float2 b1 = clamp(bCoord + float2(-blurOff, 0.0), float2(0.0), resolution);
        float2 b2 = clamp(bCoord + float2(blurOff, 0.0), float2(0.0), resolution);
        float2 b3 = clamp(bCoord + float2(0.0, -blurOff), float2(0.0), resolution);
        float2 b4 = clamp(bCoord + float2(0.0, blurOff), float2(0.0), resolution);

        half4 color;
        color.r = (
            image.eval(r0).r + image.eval(r1).r + image.eval(r2).r +
            image.eval(r3).r + image.eval(r4).r
        ) * 0.2;
        color.g = (
            image.eval(c0).g + image.eval(c1).g + image.eval(c2).g +
            image.eval(c3).g + image.eval(c4).g
        ) * 0.2;
        color.b = (
            image.eval(b0).b + image.eval(b1).b + image.eval(b2).b +
            image.eval(b3).b + image.eval(b4).b
        ) * 0.2;
        color.a = 1.0;

        // Apply a base dark glass tint across the entire card
        half3 baseFill = half3(0.08, 0.08, 0.08); // matches GlassBarFill (0x141414)
        color.rgb = mix(color.rgb, baseFill, 0.266);

        // 7. 3D curved glass shading
        // Light direction from top-left, slightly forward
        float3 L = normalize(float3(-1.0, -1.0, 1.8));
        float dotNL = clamp(dot(N, L), 0.0, 1.0);
        
        // Single clean outer highlight (no double reflection lines)
        float outerHighlight = pow(1.0 - u, 10.0) * 0.65 * (0.4 + 0.6 * dotNL);
        
        // Edge shadow: dark contour ring simulating thickness & total internal reflection
        float edgeShadow = 1.0 - pow(1.0 - u, 6.0) * 0.45 * (1.0 - 0.4 * dotNL);
        
        color.rgb *= edgeShadow;
        color.rgb += half3(outerHighlight * 0.9, outerHighlight * 0.95, outerHighlight);

        // 8. Cool glass tint fading to interior
        half3 glassTint = half3(0.92, 0.96, 1.0);
        color.rgb = mix(color.rgb, glassTint, 0.07 * (1.0 - u));

        // 9. Smoothly blend the glass overlay with the background at the outer edge
        return mix(baseColor, color, edgeAlpha);
    }

    half4 main(float2 fragCoord) {
        half4 baseColor = image.eval(fragCoord);

        // Expand bounds by 2.0 pixels to cover anti-aliased Compose borders
        bool inDensityBar = (fragCoord.x >= densityBarRect.x - 2.0 && fragCoord.x <= densityBarRect.z + 2.0 &&
                             fragCoord.y >= densityBarRect.y - 2.0 && fragCoord.y <= densityBarRect.w + 2.0);

        bool inMainBar = (fragCoord.x >= barRect.x - 2.0 && fragCoord.x <= barRect.z + 2.0 &&
                          fragCoord.y >= barRect.y - 2.0 && fragCoord.y <= barRect.w + 2.0);

        bool inSearchBar = (fragCoord.x >= searchBarRect.x - 2.0 && fragCoord.x <= searchBarRect.z + 2.0 &&
                            fragCoord.y >= searchBarRect.y - 2.0 && fragCoord.y <= searchBarRect.w + 2.0);

        bool inSearchFab = (fragCoord.x >= searchFabRect.x - 2.0 && fragCoord.x <= searchFabRect.z + 2.0 &&
                            fragCoord.y >= searchFabRect.y - 2.0 && fragCoord.y <= searchFabRect.w + 2.0);

        bool inPageIndicator = (fragCoord.x >= pageIndicatorRect.x - 2.0 && fragCoord.x <= pageIndicatorRect.z + 2.0 &&
                                fragCoord.y >= pageIndicatorRect.y - 2.0 && fragCoord.y <= pageIndicatorRect.w + 2.0);

        if (inDensityBar) {
            return applyGlassEffect(fragCoord, densityBarRect, baseColor);
        }
        if (inMainBar) {
            return applyGlassEffect(fragCoord, barRect, baseColor);
        }
        if (inSearchBar) {
            return applyGlassEffect(fragCoord, searchBarRect, baseColor);
        }
        if (inSearchFab) {
            return applyGlassEffect(fragCoord, searchFabRect, baseColor);
        }
        if (inPageIndicator) {
            return applyGlassEffect(fragCoord, pageIndicatorRect, baseColor);
        }

        bool inSelectBtn = (fragCoord.x >= selectBtnRect.x - 2.0 && fragCoord.x <= selectBtnRect.z + 2.0 &&
                            fragCoord.y >= selectBtnRect.y - 2.0 && fragCoord.y <= selectBtnRect.w + 2.0);
        if (inSelectBtn) {
            return applyGlassEffect(fragCoord, selectBtnRect, baseColor);
        }

        bool inCollapsedFab = (fragCoord.x >= collapsedFabRect.x - 2.0 && fragCoord.x <= collapsedFabRect.z + 2.0 &&
                               fragCoord.y >= collapsedFabRect.y - 2.0 && fragCoord.y <= collapsedFabRect.w + 2.0);
        if (inCollapsedFab) {
            return applyGlassEffect(fragCoord, collapsedFabRect, baseColor);
        }

        return baseColor;
    }
""".trimIndent()

fun Modifier.liquidGlassBackdrop(
    mainBarRect: () -> androidx.compose.ui.geometry.Rect,
    densityBarRect: () -> androidx.compose.ui.geometry.Rect,
    searchBarRect: () -> androidx.compose.ui.geometry.Rect,
    searchFabRect: () -> androidx.compose.ui.geometry.Rect,
    selectBtnRect: () -> androidx.compose.ui.geometry.Rect = { androidx.compose.ui.geometry.Rect.Zero },
    collapsedFabRect: () -> androidx.compose.ui.geometry.Rect = { androidx.compose.ui.geometry.Rect.Zero },
    pageIndicatorRect: () -> androidx.compose.ui.geometry.Rect = { androidx.compose.ui.geometry.Rect.Zero }
): Modifier = composed {
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        val shader = remember {
            try {
                android.graphics.RuntimeShader(LIQUID_GLASS_SHADER)
            } catch (e: Throwable) {
                null
            }
        }
        if (shader != null) {
            Modifier.graphicsLayer {
                try {
                    shader.setFloatUniform("resolution", size.width, size.height)

                    val mRect = mainBarRect()
                    val dRect = densityBarRect()
                    val sRect = searchBarRect()
                    val sfRect = searchFabRect()
                    val pRect = pageIndicatorRect()
                    val sbRect = selectBtnRect()
                    val cfRect = collapsedFabRect()

                    shader.setFloatUniform(
                        "barRect",
                        mRect.left,
                        mRect.top,
                        mRect.right,
                        mRect.bottom
                    )
                    shader.setFloatUniform(
                        "densityBarRect",
                        dRect.left,
                        dRect.top,
                        dRect.right,
                        dRect.bottom
                    )
                    shader.setFloatUniform(
                        "searchBarRect",
                        sRect.left,
                        sRect.top,
                        sRect.right,
                        sRect.bottom
                    )
                    shader.setFloatUniform(
                        "searchFabRect",
                        sfRect.left,
                        sfRect.top,
                        sfRect.right,
                        sfRect.bottom
                    )
                    shader.setFloatUniform(
                        "selectBtnRect",
                        sbRect.left,
                        sbRect.top,
                        sbRect.right,
                        sbRect.bottom
                    )
                    shader.setFloatUniform(
                        "collapsedFabRect",
                        cfRect.left,
                        cfRect.top,
                        cfRect.right,
                        cfRect.bottom
                    )
                    shader.setFloatUniform(
                        "pageIndicatorRect",
                        pRect.left,
                        pRect.top,
                        pRect.right,
                        pRect.bottom
                    )

                    renderEffect = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
                } catch (e: Throwable) {
                    // Fallback for Robolectric unit tests or preview environments
                }
            }
        } else {
            Modifier
        }
    } else {
        Modifier
    }
}
