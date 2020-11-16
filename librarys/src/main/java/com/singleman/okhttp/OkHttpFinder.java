package com.singleman.okhttp;

import java.io.Closeable;
import java.io.Flushable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import static com.singleman.okhttp.Utils.getClazzByInterface;
import static com.singleman.okhttp.Utils.getGenericClass;
import static com.singleman.okhttp.Utils.getInterfaceImpls;
import static com.singleman.okhttp.Utils.getNotStaticDeclaredFields;

/**
 * createTime: 2020/8/23.19:24
 * updateTime: 2020/8/23.19:24
 * author: singleMan.
 * desc: 根据特征寻找okhttp的类
 */
public class OkHttpFinder {

    private OkHttpFinder(){}

    private static OkHttpFinder instance = new OkHttpFinder();

    public static OkHttpFinder getInstance(){
        return instance;
    }

    private String Compat_PackageName = null;

    private boolean findTag1 = false;
    private boolean findTag2 = false;

    private List<Class> mClazzList = new ArrayList<>();

    public void findClassInit(List<Class> clazzList){
        try {
            if(null == clazzList){
                throw new NullPointerException("classList is empty !");
            }
            mClazzList.clear();
            mClazzList.addAll(clazzList);
            findTag1 = false;
            findTag2 = false;
            for(Class clazz : clazzList){
                String className = clazz.getName();
                if(!findTag1) {
                    findClientAndBuilderAndBuildAnd(clazz, className);
                }
                if(!findTag2) {
                    findOkioBuffer(clazz, className);
                }
            }
        }catch (Throwable e){
            throw e;
        }
    }


    //------------------------------------------------------------------------------------------

    /**
     * client 和 builder 和 build 方法  并吧找到的interceptors 的list 字段名称返回到回调中
     * 因为没有实例，没有办法set值，所以需要去js 中set 值
     * @param classes
     * @param className
     */
    private void findClientAndBuilderAndBuildAnd(Class classes,String className){
        if(Modifier.isFinal(classes.getModifiers())
                && Modifier.isStatic(classes.getModifiers())
        ){
            int listCount = 0;
            int finalListCount = 0;
            int listInterfaceCount = 0;
            Field[] fields = classes.getDeclaredFields();
            Field.setAccessible(fields,true);
            for (Field field : fields) {
                String type = field.getType().getName();
                if (type.contains(List.class.getName())) {
                    listCount++;
                    Class genericClass = getGenericClass(field);
                    if(null != genericClass && genericClass.isInterface()){
                        listInterfaceCount++;
                    }
                }
                if (type.contains(List.class.getName()) && Modifier.isFinal(field.getModifiers())) {
                    finalListCount++;
                }
            }
            //4个list 2个 final
            if(listCount == 4 && finalListCount == 2 && listInterfaceCount==2){
                //找到Client
                Class OkHttpClientClazz = classes.getEnclosingClass();
                if(Cloneable.class.isAssignableFrom(OkHttpClientClazz)){
                    OkCompat.Cls_OkHttpClient = OkHttpClientClazz.getName();

                    if(null != classes && null != classes.getPackage()){
                        Compat_PackageName = classes.getPackage().getName();
                    }

                    Class builderClazz = classes;

                    find_interceptor(builderClazz);

                    findClientAbout(OkHttpClientClazz);

                    findTag1 = true;
                }

            }
        }
    }


    private void find_interceptor(Class builderClazz){
        if(!checkPackage(builderClazz))return;
        Field[] declaredFields = builderClazz.getDeclaredFields();
        Field.setAccessible(declaredFields,true);
        int index = 0;
        for(Field field : declaredFields){
            if(List.class.isAssignableFrom(field.getType()) && Modifier.isFinal(field.getModifiers())
                    && getGenericClass(field).isInterface()
            ){
                if(index == 0) {
                    //添加自己的Interceptor,提供回调给js调用
                    findInterceptor(field);
                    index++;
                }
            }
        }
    }

    private void findClientAbout(Class clientClazz){
        if(!checkPackage(clientClazz))return;

        Method[] declaredMethods = clientClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){
            if(Utils.getParameterCount(m) == 1 && m.getReturnType().isInterface()){
                // newCall
                OkCompat.Cls_Request = m.getParameterTypes()[0].getName();

                OkCompat.M_Client_newCall = m.getName();

                OkCompat.Cls_Call = m.getReturnType().getName();

                findCallAbout(m.getReturnType());

            }
        }
    }


    /**
     * 获取 Call 相关
     */
    private void findCallAbout(Class callClazz){
        Class callImplClazz = getClazzByInterface(mClazzList,callClazz);
        if(null == callImplClazz){
            throw new NullPointerException("not find [Call] impl clazz!");
        }

        Method[] declaredMethods = callImplClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){

            if("".equals(OkCompat.Cls_Response)){
                if(m.toGenericString().startsWith("public") && Utils.getParameterCount(m) == 0
                        && Modifier.isFinal(m.getReturnType().getModifiers())
                        && !void.class.isAssignableFrom(m.getReturnType())
                ) {
                    if("".equals(OkCompat.M_Call_execute)){
                        OkCompat.M_Call_execute = m.getName();
                    }
                    OkCompat.Cls_Response = m.getReturnType().getName();
                }
            }else {
                if(m.toGenericString().startsWith("public") && Utils.getParameterCount(m) == 0
                        && Modifier.isFinal(m.getReturnType().getModifiers())
                        && !void.class.isAssignableFrom(m.getReturnType())
                        && m.toGenericString().contains(OkCompat.Cls_Response)
                ) {
                    if("".equals(OkCompat.M_Call_execute)){
                        OkCompat.M_Call_execute = m.getName();
                    }
                    OkCompat.Cls_Response = m.getReturnType().getName();
                }
            }

            if(OkCompat.Cls_Request == m.getReturnType().getName()
                    && Utils.getParameterCount(m) == 0
            ){
                OkCompat.M_Call_request = m.getName();

            }

//            if(Utils.getParameterCount(m) == 0 && callClazz.getName().equals(m.getReturnType().getName())){
//                OkCompat.M_Call_clone = m.getName();
//                OKLog.debugLog(String.format(format,"M_Call_clone",OkCompat.M_Call_clone));
//            }
        }

        for(Method m : declaredMethods){
            if(Utils.getParameterCount(m) == 1 && m.getParameterTypes()[0].isInterface()){
                OkCompat.M_Call_enqueue = m.getName();

                OkCompat.Cls_CallBack = m.getParameterTypes()[0].getName();

                findCallBackAbout(m.getParameterTypes()[0]);
            }
        }

    }


    /**
     * 网络请求回调相关
     * @param callbackClazz
     */
    private void findCallBackAbout(Class callbackClazz){

        Method[] declaredMethods = callbackClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){

            if(Utils.getParameterCount(m) == 2 && !Exception.class.isAssignableFrom(m.getParameterTypes()[1])){
                OkCompat.M_CallBack_onResponse = m.getName();

            }
            if(Utils.getParameterCount(m) == 2 && Exception.class.isAssignableFrom(m.getParameterTypes()[1])){
                OkCompat.M_CallBack_onFailure = m.getName();
            }
        }
    }

    /**
     * 获取interceptor的类
     * @param interceptorsField
     */
    private void findInterceptor(Field interceptorsField){
        //也可以获取到泛型的类名
        Class interceptorClass = getGenericClass(interceptorsField);
        if(!checkPackage(interceptorClass))return;

        Method[] declaredMethods = interceptorClass.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        if(null != declaredMethods && declaredMethods.length == 1){
            Method interceptMethod = declaredMethods[0];
            //intercept 的返回值是 Response
//            Class<?> returnType = declaredMethod.getReturnType();
            Class<?>[] parameterTypes = interceptMethod.getParameterTypes();
            if(null != parameterTypes && parameterTypes.length == 1){
                //chain
                Class<?> chainClazz = parameterTypes[0];
                findChainAbout(chainClazz,interceptMethod.getReturnType());
            }

        }

    }

    /**
     * 寻找 chain 相关
     * @param chainClazz
     */
    private void findChainAbout(Class chainClazz,Class interceptMethodReturnType){
        if(!checkPackage(chainClazz))return;
        Class chainImplClazz = getClazzByInterface(mClazzList,chainClazz);
        if(null == chainImplClazz){
            throw  new NullPointerException("not find [chain] impl clazz!");
        }
        Method[] chainMethods = chainImplClazz.getMethods();
        Method.setAccessible(chainMethods,true);
        for(Method m : chainMethods){
            Class<?> mReturnClazz = m.getReturnType();
            int mArgCount = Utils.getParameterCount(m);
            if(!mReturnClazz.isInterface() && mArgCount == 1 && interceptMethodReturnType.isAssignableFrom(mReturnClazz)){
                //request
                Class requestClazz = m.getParameterTypes()[0];
                findRequestAbout(requestClazz);

                //Response
                Class<?> responseClazz = m.getReturnType();

                OkCompat.Cls_Response = responseClazz.getName();

                OkCompat.Cls_Request = requestClazz.getName();

                findResponseAbout(responseClazz,requestClazz);
            }
        }
    }

    private Class Compat_responseClazz = null;

    /**
     * 响应
     * @param responseClazz
     * requestClazz 为了寻找 request 方法  因为这个是他的返回类型
     */
    private void findResponseAbout(Class responseClazz,Class requestClazz) {
        if(!checkPackage(responseClazz))return;

        Compat_responseClazz = responseClazz;

        //获取内部类 builder
        Class Response$BuilderClazz=null;
        Class[] innerClazz = responseClazz.getDeclaredClasses();
        for (Class cls : innerClazz) {
            if(Modifier.isStatic(cls.getModifiers())){
                Response$BuilderClazz = cls;
            }
        }
        //内部类的方法
        if(null != Response$BuilderClazz){
            Method[] declaredMethods = Response$BuilderClazz.getDeclaredMethods();
            Method.setAccessible(declaredMethods,true);
            for(Method m : declaredMethods){

//                if(Response$BuilderClazz.isAssignableFrom(m.getReturnType()) && Utils.getParameterCount(m) == 1
//                        && Modifier.isAbstract(m.getParameterTypes()[0].getModifiers())
//                        && !m.getParameterTypes()[0].isPrimitive()
//                ){
//                    OkCompat.M_rsp$builder_body = m.getName();
//                    OKLog.debugLog(String.format(format,"M_rsp$builder_body",OkCompat.M_rsp$builder_body));
//                }


                if(responseClazz.isAssignableFrom(m.getReturnType()) && Utils.getParameterCount(m) == 0
                        && !Modifier.isFinal(m.getModifiers())
                ){
                    OkCompat.M_rsp$builder_build = m.getName();
                }
            }

            Field[] declaredFields = Response$BuilderClazz.getDeclaredFields();
            Field.setAccessible(declaredFields,true);
            for(Field f:declaredFields){
                if(Modifier.isAbstract(f.getType().getModifiers()) && checkPackage(f.getType()) && !f.getType().isInterface()){
                    OkCompat.F_rsp$builder_body = f.getName();

                }
            }

        }

        //方法
        Field[] declaredFields = responseClazz.getDeclaredFields();
        Field.setAccessible(declaredFields,true);
        for(Field f : declaredFields){
            Class typeClazz = f.getType();
            if(Modifier.isAbstract(typeClazz.getModifiers()) && checkPackage(typeClazz) && !typeClazz.isInterface()){
                OkCompat.F_rsp_body = f.getName();

                //寻找ResponseBody 的相关
                findResponseBodyAbout(typeClazz);
            }
            //Code
            if(int.class.isAssignableFrom(typeClazz)){
                OkCompat.F_rsp_code = f.getName();
            }

            //String
            if(String.class.isAssignableFrom(typeClazz)){
                OkCompat.F_rsp_message = f.getName();
            }
            //request
            if(requestClazz.isAssignableFrom(typeClazz) && checkPackage(typeClazz)){
                OkCompat.F_rsp_request = f.getName();
            }
            Field[] notStaticDeclaredFields = getNotStaticDeclaredFields(typeClazz);
            //headers
            if(Modifier.isFinal(typeClazz.getModifiers())
                    && checkPackage(typeClazz)
                    && null != notStaticDeclaredFields && notStaticDeclaredFields.length == 1){
                if(String[].class.isAssignableFrom(notStaticDeclaredFields[0].getType())){
                    OkCompat.F_rsp_headers = f.getName();
                }
            }

        }

        //方法
        Method[] declaredMethods = responseClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){
            Class returnClazz = m.getReturnType();
            //返回  newBuilder
            if(Utils.getParameterCount(m) == 0 && null != Response$BuilderClazz && Response$BuilderClazz.isAssignableFrom(returnClazz)){
                OkCompat.M_rsp_newBuilder = m.getName();
            }
        }
    }

    /**
     *
     * @param responseBodyClazz
     */
    private void findResponseBodyAbout(Class responseBodyClazz){
        if(!checkPackage(responseBodyClazz))return;
        OkCompat.Cls_ResponseBody = responseBodyClazz.getName();

        Method[] declaredMethods = responseBodyClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){
            Class returnClazz = m.getReturnType();
            //返回值long类型 contentLength
            if(long.class.isAssignableFrom(returnClazz) && Utils.getParameterCount(m) == 0){
                OkCompat.M_rspBody_contentLength = m.getName();
            }

            //无参数
            if(Utils.getParameterCount(m) == 0 && m.toGenericString().contains("abstract") && Modifier.isFinal(returnClazz.getModifiers())
                    && !returnClazz.isPrimitive()
            ){
                OkCompat.M_rspBody_contentType = m.getName();
            }
            //无参数
            if(Utils.getParameterCount(m) == 0 && m.toGenericString().contains("abstract") && returnClazz.isInterface()){
                OkCompat.M_rspBody_source = m.getName();

            }

            //2个参数
            if(Utils.getParameterCount(m) == 2 && responseBodyClazz.isAssignableFrom(returnClazz)
                    && (String.class.isAssignableFrom(m.getParameterTypes()[1]) || byte[].class.isAssignableFrom(m.getParameterTypes()[1]))
            ){
                OkCompat.M_rspBody_create = m.getName();
            }



        }
    }


    private void findRequestAbout(Class requestClazz){
        if(!checkPackage(requestClazz))return;
        Field[] declaredFields = requestClazz.getDeclaredFields();
        Field.setAccessible(declaredFields,true);
        for(Field f : declaredFields){
            Class typeClazz = f.getType();
            //返回类型是抽象类  body
            if(Modifier.isAbstract(typeClazz.getModifiers()) && checkPackage(typeClazz) && !typeClazz.isInterface()){
                OkCompat.F_req_body = f.getName();
                //寻找requestBody 的相关
                findRequestBodyAbout(typeClazz);
            }
            //String  method
            if(String.class.isAssignableFrom(typeClazz)){
                OkCompat.F_req_method = f.getName();
            }

            Field[] notStaticDeclaredFields = getNotStaticDeclaredFields(typeClazz);
            //返回类里面就只有一个数组字段  headers
            if(Modifier.isFinal(typeClazz.getModifiers()) && null != notStaticDeclaredFields && notStaticDeclaredFields.length == 1){
                if(String[].class.isAssignableFrom(notStaticDeclaredFields[0].getType())){
                    OkCompat.F_req_headers = f.getName();
                    //Headers
                    findHeadersAbout(typeClazz);
                }
            }
            //url
            if(Modifier.isFinal(typeClazz.getModifiers()) && f.toGenericString().indexOf("volatile") < 0
                    //并且不是headers
                    && !(null != notStaticDeclaredFields && notStaticDeclaredFields.length == 1 && String[].class.isAssignableFrom(notStaticDeclaredFields[0].getType()))
                    && !String.class.isAssignableFrom(typeClazz)
                    && checkPackage(typeClazz)
            ){
                OkCompat.F_req_url = f.getName();

            }
        }
    }


    /**
     *
     * @param requestBodyClazz
     */
    private void findRequestBodyAbout(Class requestBodyClazz){
        if(!checkPackage(requestBodyClazz))return;
        Method[] declaredMethods = requestBodyClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){
            Class returnClazz = m.getReturnType();
            //返回值long类型 contentLength
            if(long.class.isAssignableFrom(returnClazz) && Utils.getParameterCount(m) == 0){
                OkCompat.M_reqbody_contentLength = m.getName();
            }
            //判断返回 void  writeTo
            if(Modifier.isAbstract(m.getModifiers()) && Utils.getParameterCount(m) == 1
                    && Flushable.class.isAssignableFrom(m.getParameterTypes()[0])
                    && Closeable.class.isAssignableFrom(m.getParameterTypes()[0])
            ){
                OkCompat.M_reqbody_writeTo = m.getName();

                Class BufferedSinkClazz = m.getParameterTypes()[0];

                List<Class> interfaceImpls = getInterfaceImpls(mClazzList, BufferedSinkClazz);
                if(null != interfaceImpls) {
                    for (Class c : interfaceImpls) {
                        if (null != c.getInterfaces() && c.getInterfaces().length > 1) {
                            OkCompat.Cls_okio_Buffer = c.getName();

                            Method[] Buffer_declaredMethods = c.getDeclaredMethods();
                            Method.setAccessible(Buffer_declaredMethods, true);
                            for (Method b_m : Buffer_declaredMethods) {
                                Class<?> b_returnClazz = b_m.getReturnType();
                                if (byte[].class.isAssignableFrom(b_returnClazz) && Utils.getParameterCount(b_m) == 0) {
                                    OkCompat.M_buffer_readByteArray = b_m.getName();
                                }
                            }
                        }
                    }
                }
            }

            //无参数  并且 返回的不是 自己
            if(Utils.getParameterCount(m) == 0 && !requestBodyClazz.isAssignableFrom(returnClazz)
                    && m.toGenericString().contains("abstract")
            ){
                OkCompat.M_reqbody_contentType = m.getName();

                //MediaType chatset
                findMediaTypeAbout(returnClazz);
            }
        }
    }

    /**
     *
     * @param mediaTypeClazz
     */
    private void findMediaTypeAbout(Class mediaTypeClazz) {
        if(!checkPackage(mediaTypeClazz))return;
        Method[] declaredMethods = mediaTypeClazz.getDeclaredMethods();
        Method.setAccessible(declaredMethods,true);
        for(Method m : declaredMethods){
            Class<?> returnClazz = m.getReturnType();
            if(Charset.class.isAssignableFrom(returnClazz)
                    && Utils.getParameterCount(m) == 1
                    && Charset.class.isAssignableFrom(m.getParameterTypes()[0])
            ){
                OkCompat.M_contentType_charset = m.getName();
            }
        }
    }


    /**
     *
     * @param headerClazz
     */
    private void findHeadersAbout(Class headerClazz){
        if(!checkPackage(headerClazz))return;
        Field[] fields = headerClazz.getDeclaredFields();
        Field.setAccessible(fields,true);
        for(Field f : fields){
            Class<?> typeClazz = f.getType();
            if(String[].class.isAssignableFrom(typeClazz)){
                OkCompat.F_header_namesAndValues = f.getName();
            }
        }

    }


    /**
     * okio.buffer 相关
     * @param classes
     * @param className
     */
    private void findOkioBuffer(Class classes,String className) {
        if(Modifier.isFinal(classes.getModifiers())
                && Cloneable.class.isAssignableFrom(classes)
        ){
            Field[] declaredFields = classes.getDeclaredFields();
            Field.setAccessible(declaredFields,true);
            Class okioBufferClazz = null;
            for(Field field : declaredFields){
                try {
                    if(byte[].class.isAssignableFrom(field.getType())
                            && Modifier.isStatic(field.getModifiers())
                            && ((byte[])field.get(null)).length == 16
                    ){
                        //TODO  Okio.Buffer
                        okioBufferClazz = classes;
                        break;
                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            if(null != okioBufferClazz) {

                findTag2 = true;

                OkCompat.Cls_okio_Buffer = okioBufferClazz.getName();

                Method[] declaredMethods = okioBufferClazz.getDeclaredMethods();
                Method.setAccessible(declaredMethods, true);
                for(Method m : declaredMethods){
                    Class<?> returnClazz = m.getReturnType();
                    if(byte[].class.isAssignableFrom(returnClazz) && Utils.getParameterCount(m) == 0){
                        OkCompat.M_buffer_readByteArray = m.getName();
                    }

//                    if(int.class.isAssignableFrom(returnClazz) && Utils.getParameterCount(m) == 0){
//                        if(m.toGenericString().contains("EOFException")){
//                            OkCompat.M_buffer_readUtf8CodePoint = m.getName();
//                            OKLog.debugLog(String.format(format,"M_buffer_readUtf8CodePoint",OkCompat.M_buffer_readUtf8CodePoint));
//                        }
//                    }
                }
            }

        }

    }




//    private void findHttpHeaders(Class<?> loadClass, String name,Class responseClazz) {
//        if(null == loadClass)return;
//        if(Modifier.isFinal(loadClass.getModifiers()) && !loadClass.isInterface()
//        ){
//            Method[] declaredMethods = loadClass.getDeclaredMethods();
//            Method.setAccessible(declaredMethods,true);
//
//            int matchCount = 0;
//            for(Method m : declaredMethods){
//                if(boolean.class.isAssignableFrom(m.getReturnType())
//                        && Utils.getParameterCount(m) == 1
//                        && responseClazz.isAssignableFrom(m.getParameterTypes()[0])
//                ){
//                    matchCount++;
//                }
//            }
//            OKLog.debugLog(">>>>>>>>>>>>>>>>>>>matchCount>>>>>>> : "+ matchCount+" : "+loadClass.getName());
//            if(matchCount == 2){
//                OkCompat.Cls_HttpHeaders = name;
//                OKLog.debugLog("findHttpHeaders : "+name);
//
//                Method[] methods = loadClass.getDeclaredMethods();
//                Method.setAccessible(methods,true);
//                int hasBodyIndex = 0;
//                for(Method mm : methods){
//                    if(hasBodyIndex == 1){
//                        OkCompat.M_HttpHeaders_hasBody = mm.getName();
//                        OKLog.debugLog("M_HttpHeaders_hasBody : "+OkCompat.M_HttpHeaders_hasBody+"  " +mm.toGenericString());
//                    }
//                    hasBodyIndex++;
//                }
//            }
//        }
//    }

    /**
     *
     * @param checkClazz
     * @return
     */
    public boolean checkPackage(Class checkClazz){
        if(null != checkClazz
                && null != checkClazz.getPackage()
                && (checkClazz.getPackage().getName().startsWith(Compat_PackageName) || checkClazz.getPackage().getName().startsWith("okhttp"))
        ){
            return true;
        }
        return false;
    }
}
