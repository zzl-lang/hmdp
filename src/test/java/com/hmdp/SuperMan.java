package com.hmdp;

public class SuperMan extends Hero{
    @Override
    public String name(){
        return "superman";
    }
    public Hero hero(){
        return new Hero();
    }
    public static void main(String[] args){
        String str1 = "hello";
        int a = 1;
        int b = 2;
        System.out.println(a==b);

        System.out.println(str1.getClass());
        System.out.println(str1.hashCode());
        System.out.println(str1.toString());
    }


}
