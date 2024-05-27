/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef agdktunnel_text_renderer_hpp
#define agdktunnel_text_renderer_hpp

#include "common.hpp"
#include "simplegeom.hpp"
#include "simple_renderer/renderer_uniform_buffer.h"

/* Renders text to the screen. Uses the "normalized 2D coordinate system" as
 * described in the README. */
class TextRenderer {
 public:
  static const int CHAR_CODES = 128;
  SimpleGeom *mCharGeom[CHAR_CODES];

  float mFontScale;
  float mColor[4];
  glm::mat4 mMatrix;
  std::shared_ptr<simple_renderer::UniformBuffer> mUniformBuffer;

 public:
  TextRenderer(std::shared_ptr<simple_renderer::UniformBuffer> uniformBuffer);

  ~TextRenderer();

  void SetMatrix(glm::mat4 mat);

  void SetFontScale(float size);

  void RenderText(const char *str, float centerX, float centerY);

  void SetColor(float r, float g, float b) {
    mColor[0] = r, mColor[1] = g, mColor[2] = b;
  }

  void SetColor(const float *c) {
    mColor[0] = c[0], mColor[1] = c[1], mColor[2] = c[2];
  }

  void ResetColor() {
    SetColor(1.0f, 1.0f, 1.0f);
  }

  void ResetMatrix() {
    SetMatrix(glm::mat4(1.0f));
  }

  static void MeasureText(const char *str, float fontScale,
                          float *outWidth, float *outHeight);

  static float MeasureTextWidth(const char *str, float fontScale) {
    float w;
    TextRenderer::MeasureText(str, fontScale, &w, NULL);
    return w;
  }

  static float MeasureTextHeight(const char *str, float fontScale) {
    float h;
    TextRenderer::MeasureText(str, fontScale, NULL, &h);
    return h;
  }
};

#endif
