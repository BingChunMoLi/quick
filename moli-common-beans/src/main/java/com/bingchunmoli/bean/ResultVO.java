package com.bingchunmoli.bean;


import com.bingchunmoli.bean.enums.CodeEnum;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用返回Result
 *
 * @author BingChunMoLi
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultVO<T> {

    /**
     * 业务状态码
     */
    @Schema(name = "业务状态码")
    @ApiModelProperty("业务状态码")
    private String code;

    /**
     * 业务友好消息
     */
    @Schema(name = "业务友好消息")
    @ApiModelProperty("业务友好消息")
    private String msg;

    /**
     * 业务承载数据
     */
    @Schema(name = "业务承载数据")
    @ApiModelProperty("业务承载数据")
    private T data;

    public ResultVO(Code code, T data) {
        this.code = code.getCode();
        this.msg = code.getMsg();
        this.data = data;
    }


    public ResultVO(T data) {
        this(CodeEnum.SUCCESS, data);
    }

    public static <T> ResultVO<T> ok(T data) {
        return new ResultVO<>(data);
    }

    public static <T> ResultVO<T> failBySystem(T data) {
        return new ResultVO<>(CodeEnum.FAILURE, data);
    }

    public static <T> ResultVO<T> failBySystem(Code code, T data) {
        return new ResultVO<>(code, data);
    }
}
