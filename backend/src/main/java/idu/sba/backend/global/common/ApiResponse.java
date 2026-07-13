package idu.sba.backend.global.common;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final String  message;  //사람이 읽는 메세지
    private final T data; //실제 응답 데이터


    private ApiResponse(boolean success, String message, T data){
        this.success = success;
        this.message = message;
        this.data = data;
    }

    //데이터만 담아 성공
    public static <T> ApiResponse<T> ok(T data){
        return new ApiResponse<>(true,"성공",data);
    }

    //데이터 + 메세지 성공
    public static <T> ApiResponse<T> ok(String message,T data){
        return new ApiResponse<>(true,message ,data);
    }

    //데이터 없이 메세지만 성공
    public static ApiResponse<Void> ok(String message){
        return new ApiResponse<>(true,message ,null);
    }

    //실패 응답(에러 메세지만)
    public static ApiResponse<Void> fail(String message){
        return new ApiResponse<>(false,message,null);
    }





}
