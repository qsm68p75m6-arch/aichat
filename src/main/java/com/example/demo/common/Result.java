//commom 放全局的东西，所有Controller都用
package com.example.demo.common;

public class Result {
    private boolean success;
    private String message;
    private Object data;
    public Result(boolean success, String message,Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }
    public static Result ok(Object data){
    return new Result(true,"操作成功",data);
    }
    public static  Result fail(String message){
    return new Result(false,message,null);
        }
    public boolean isSuccess(){
        return success;
    }
    public String getMessage(){
        return message;
    }
    public Object getData(){
        return data;
    }


}

