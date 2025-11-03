package com.example.oidcclient.aspect;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
@Component
public class LoggingAspect {

    // パッケージをプロジェクトのルートに合わせて調整（com.example.oidcclient 以下すべて）
    @Before("execution(* com.example.oidcclient..*(..))")
    public void logMethodStart(JoinPoint joinPoint) {
        // ターゲットのクラス名とメソッド名を取得
        String declaringTypeName = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();

        // ターゲットクラスの Logger を取得して出力（そのクラスのログ設定に従う）
        Logger targetLogger = LoggerFactory.getLogger(declaringTypeName);
        if (targetLogger.isDebugEnabled()) {
            targetLogger.debug("start method: {}.{}", declaringTypeName, methodName);
        }
    }
}