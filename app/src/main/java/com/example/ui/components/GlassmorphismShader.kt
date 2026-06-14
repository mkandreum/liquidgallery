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

        // If outside the rounded pill boundary, return background as-is (keeps perfect pill shape)
        if (sdf > 0.0) return baseColor;

        // 2. Calculate edge factor (0 at center, 1 at edge) and surface normal
        float edgeFactor = clamp(1.0 + sdf / r, 0.0, 1.0);

        // Wider curvature profile — "ángulo más abierto"
        float edgeDepth = pow(edgeFactor, 0.85);

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

        // 4. Refraction displacement — subtle glass-lens warp
        float2 distortedCoord = coord + dir * (14.0 * edgeDepth);

        // 5. Chromatic aberration: split RGB radially
        float chromaStrength = 5.0 * pow(edgeFactor, 0.9);
        float2 rCoord = distortedCoord + dir * chromaStrength;
        float2 bCoord = distortedCoord - dir * chromaStrength * 0.75;

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

        // 7. 3D curved glass shading
        // Light from upper-left: surfaces facing upper-left catch the light
        float lightDot = dir.x * 0.707 + dir.y * 0.707; // -1 (shadow) to 1 (lit)

        // Edge shadow — simulates glass thickness curving away from light
        float shadow = 1.0 - edgeDepth * 0.28 * clamp(1.0 - lightDot, 0.0, 1.0);
        color.rgb *= shadow;

        // Rim light — glass edge catching light on the lit side
        float rimLight = edgeDepth * 0.18 * clamp(lightDot, 0.0, 1.0);
        color.rgb += half3(rimLight * 0.92, rimLight * 0.96, rimLight);

        // Specular reflection band — bright highlight at the curvature peak
        float specular = pow(edgeDepth, 3.0) * 0.35 * pow(clamp(lightDot, 0.0, 1.0), 3.0);
        color.rgb += half3(specular * 0.85, specular * 0.92, specular);

        // 8. Blue-white glass tint fading to edges
        half3 glassTint = half3(0.92, 0.95, 1.0);
        color.rgb = mix(color.rgb, glassTint, 0.06 * edgeFactor);

        return color;
    }

    half4 main(float2 fragCoord) {
        half4 baseColor = image.eval(fragCoord);

        bool inDensityBar = (fragCoord.x >= densityBarRect.x && fragCoord.x <= densityBarRect.z &&
                             fragCoord.y >= densityBarRect.y && fragCoord.y <= densityBarRect.w);

        bool inMainBar = (fragCoord.x >= barRect.x && fragCoord.x <= barRect.z &&
                          fragCoord.y >= barRect.y && fragCoord.y <= barRect.w);

        bool inSearchBar = (fragCoord.x >= searchBarRect.x && fragCoord.x <= searchBarRect.z &&
                            fragCoord.y >= searchBarRect.y && fragCoord.y <= searchBarRect.w);

        bool inSearchFab = (fragCoord.x >= searchFabRect.x && fragCoord.x <= searchFabRect.z &&
                            fragCoord.y >= searchFabRect.y && fragCoord.y <= searchFabRect.w);

        bool inPageIndicator = (fragCoord.x >= pageIndicatorRect.x && fragCoord.x <= pageIndicatorRect.z &&
                                fragCoord.y >= pageIndicatorRect.y && fragCoord.y <= pageIndicatorRect.w);

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

        bool inSelectBtn = (fragCoord.x >= selectBtnRect.x && fragCoord.x <= selectBtnRect.z &&
                            fragCoord.y >= selectBtnRect.y && fragCoord.y <= selectBtnRect.w);
        if (inSelectBtn) {
            return applyGlassEffect(fragCoord, selectBtnRect, baseColor);
        }

        bool inCollapsedFab = (fragCoord.x >= collapsedFabRect.x && fragCoord.x <= collapsedFabRect.z &&
                               fragCoord.y >= collapsedFabRect.y && fragCoord.y <= collapsedFabRect.w);
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
