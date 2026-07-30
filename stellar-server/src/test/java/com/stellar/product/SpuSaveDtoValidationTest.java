package com.stellar.product;

import com.stellar.dto.SpuSaveDTO;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RED-GREEN: 验证「前端表单风格」的 payload 在 @Valid 校验下的行为。
 * 前端 SpuMgmt.vue 提交的字段：name / categoryId / price(Number) / image(String) / description(String) / status(1|0) / id(null 或 Long)。
 * —— 修复前：descriptionMd 上 @NotBlank 必失败，且缺 mainImage。
 * —— 修复后：放宽 descriptionMd 校验 + 增加 image/mainImage/description/descriptionMd 互相兼容，前端表单可直接校验通过。
 */
class SpuSaveDtoValidationTest {

    private final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private final Validator validator = factory.getValidator();

    private SpuSaveDTO frontendLikeForm() {
        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName("星耀 55 寸 4K 电视");
        dto.setCategoryId(1L);
        dto.setStatus(1);
        // ↓↓↓ 前端实际只传这些（没有 descriptionMd / mainImage / skuList）
        dto.setImage("https://cdn.example.com/tv.jpg");
        dto.setDescription("<p>HTML 描述：屏幕好 画质棒</p>");
        dto.setPrice(new BigDecimal("3299.00"));
        return dto;
    }

    // ============================================================
    // RED: 修复前，以下 3 条校验应能覆盖到「前端保存失败」的根因。
    //      为了先证明 fail-for-expected-reason，我们把断言写成"当前有哪些违规"
    // ============================================================
    @Test
    void frontendFormFields_shouldPassValidation_afterFix() {
        SpuSaveDTO dto = frontendLikeForm();

        Set<ConstraintViolation<SpuSaveDTO>> violations = validator.validate(dto);

        // ✅ GREEN 目标：前端表单风格提交必须校验通过（0 违规）。
        //    修复前这里通常会有 1 个违规：descriptionMd 不能为空。
        assertTrue(violations.isEmpty(),
                "期望前端表单风格字段可直接通过 @Valid；实际违规：" +
                        violations.stream()
                                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                                .toList());
    }

    @Test
    void descriptionMdAndDescription_bothPresent_thenValidationStillPasses() {
        SpuSaveDTO dto = frontendLikeForm();
        dto.setDescriptionMd("## MD 描述\n- 亮点 1");

        Set<ConstraintViolation<SpuSaveDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "md+html 双描述都有，不该触发任何违规");
    }

    @Test
    void nameBlank_shouldStillFail() {
        SpuSaveDTO dto = frontendLikeForm();
        dto.setName("   ");

        Set<ConstraintViolation<SpuSaveDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty(), "空名称应当仍然失败");
        assertTrue(violations.stream().anyMatch(v -> "name".equals(v.getPropertyPath().toString())),
                "应包含 name 字段的违规");
    }

    @Test
    void categoryIdNull_shouldStillFail() {
        SpuSaveDTO dto = frontendLikeForm();
        dto.setCategoryId(null);
        Set<ConstraintViolation<SpuSaveDTO>> v2 = validator.validate(dto);
        assertTrue(v2.stream().anyMatch(v -> "categoryId".equals(v.getPropertyPath().toString())),
                "categoryId=null 应触发 @NotNull");
    }
}
