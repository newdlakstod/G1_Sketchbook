package com.gdo.pagecurl

internal object ShaderSources {
    const val PAGE_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec2 aUv;

        uniform mat4 uMvp;
        uniform float uPageOffsetX;

        out vec2 vUv;
        out vec2 vPagePosition;

        void main() {
            vec3 worldPosition = aPosition + vec3(uPageOffsetX, 0.0, 0.0);
            gl_Position = uMvp * vec4(worldPosition, 1.0);
            vUv = aUv;
            vPagePosition = worldPosition.xy;
        }
    """

    const val PAGE_FRAGMENT = """#version 300 es
        precision mediump float;

        in vec2 vUv;
        in vec2 vPagePosition;

        uniform sampler2D uFrontTexture;
        uniform sampler2D uBackTexture;
        uniform float uStaticPage;
        uniform float uBlankPage;
        uniform float uGutterSide;
        uniform float uGutterWidth;
        uniform float uGutterOpacity;
        uniform vec2 uShadowAxisPoint;
        uniform vec2 uShadowNormal;
        uniform float uShadowWidth;
        uniform float uShadowOpacity;

        out vec4 fragColor;

        vec3 desaturate(vec3 color, float amount) {
            float gray = dot(color, vec3(0.299, 0.587, 0.114));
            return mix(color, vec3(gray), amount);
        }

        float paperNoise(vec2 uv) {
            vec2 cell = floor(uv * vec2(320.0, 420.0));
            return fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        }

        void main() {
            vec4 front = texture(uFrontTexture, vUv);
            vec4 back = texture(uBackTexture, vec2(1.0 - vUv.x, vUv.y));
            back.rgb = desaturate(back.rgb, 0.12) * 0.94;
            vec4 turningPaper = gl_FrontFacing ? front : back;
            vec4 paper = uStaticPage < 0.5 ? turningPaper : front;

            if (uStaticPage > 0.5 && uBlankPage > 0.5) {
                float noise = paperNoise(vUv) * 0.024;
                paper = vec4(vec3(0.92, 0.89, 0.80) + noise, 1.0);
            }

            if (uStaticPage > 0.5 && uShadowOpacity > 0.0 && uShadowWidth > 0.0) {
                float distanceIntoCurl = dot(vPagePosition - uShadowAxisPoint, uShadowNormal);
                float shadowBand = step(0.0, distanceIntoCurl) *
                    (1.0 - smoothstep(0.0, uShadowWidth, distanceIntoCurl));
                paper.rgb *= 1.0 - uShadowOpacity * shadowBand;
            }

            if (uStaticPage > 0.5 && uGutterOpacity > 0.0 && uGutterWidth > 0.0) {
                float gutter = 0.0;
                if (uGutterSide < -0.5) {
                    gutter = smoothstep(1.0 - uGutterWidth, 1.0, vUv.x);
                } else if (uGutterSide > 0.5) {
                    gutter = 1.0 - smoothstep(0.0, uGutterWidth, vUv.x);
                }
                paper.rgb *= 1.0 - uGutterOpacity * gutter;
            }

            fragColor = vec4(paper.rgb, 1.0);
        }
    """

}
