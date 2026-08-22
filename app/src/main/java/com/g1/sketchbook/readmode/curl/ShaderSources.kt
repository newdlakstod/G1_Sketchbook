package com.g1.sketchbook.readmode.curl

object ShaderSources {
    const val PAGE_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in vec2 aUv;
        layout(location = 2) in float aShade;
        layout(location = 3) in float aSide;

        uniform mat4 uMvp;

        out vec2 vUv;
        out float vShade;
        out float vSide;

        void main() {
            gl_Position = uMvp * vec4(aPosition, 1.0);
            vUv = aUv;
            vShade = aShade;
            vSide = aSide;
        }
    """

    const val PAGE_FRAGMENT = """#version 300 es
        precision mediump float;

        in vec2 vUv;
        in float vShade;
        in float vSide;

        uniform sampler2D uFrontTexture;
        uniform sampler2D uBackTexture;
        uniform float uStaticPage;

        out vec4 fragColor;

        vec3 desaturate(vec3 color, float amount) {
            float gray = dot(color, vec3(0.299, 0.587, 0.114));
            return mix(color, vec3(gray), amount);
        }

        void main() {
            bool backFacing = !gl_FrontFacing || vSide > 0.55;
            vec4 front = texture(uFrontTexture, vUv);
            vec4 back = texture(uBackTexture, vec2(1.0 - vUv.x, vUv.y));
            vec4 paper = backFacing && uStaticPage < 0.5 ? back : front;
            if (backFacing && uStaticPage < 0.5) {
                paper.rgb = desaturate(paper.rgb, 0.12) * 0.94;
            }
            float ridgeHighlight = 0.05 * smoothstep(1.0, 1.07, vShade);
            paper.rgb *= clamp(vShade + ridgeHighlight, 0.46, 1.10);
            fragColor = paper;
        }
    """

    const val SHADOW_VERTEX = """#version 300 es
        layout(location = 0) in vec3 aPosition;
        layout(location = 1) in float aAlpha;

        uniform mat4 uMvp;

        out float vAlpha;

        void main() {
            gl_Position = uMvp * vec4(aPosition, 1.0);
            vAlpha = aAlpha;
        }
    """

    const val SHADOW_FRAGMENT = """#version 300 es
        precision mediump float;

        in float vAlpha;
        out vec4 fragColor;

        void main() {
            fragColor = vec4(0.08, 0.07, 0.06, vAlpha);
        }
    """
}
