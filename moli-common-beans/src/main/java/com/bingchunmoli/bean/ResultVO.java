package com.bingchunmoli.bean;


import com.bingchunmoli.bean.enums.CodeEnum;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty("业务状态码")
    private String code;

    /**
     * 业务友好消息
     */
    @ApiModelProperty("业务友好消息")
    private String msg;

    /**
     * 业务承载数据
     */
    @ApiModelProperty("业务承载数据")
    private T data;

    public ResultVO(CodeEnum code, T data) {
        this.code = code.getCode();
        this.msg = code.getMsg();
        this.data = data;
    }


    public ResultVO(T data) {
        this(CodeEnum.SUCCESS, data);
    }

    public static <T> ResultVO<T> ok(T data) {
        new ResultVO<Object>();
        return new ResultVO<>(data);
    }

    public static <T> ResultVO<T> failBySystem(T data) {
        return new ResultVO<>(CodeEnum.FAILURE, data);
    }
}
