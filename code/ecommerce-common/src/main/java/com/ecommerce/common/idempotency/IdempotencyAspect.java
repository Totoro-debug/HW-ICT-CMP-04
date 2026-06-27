package com.ecommerce.common.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final IdempotencyRecordRepository repository;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotencyAspect(IdempotencyRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(idempotent)")
    @Transactional
    public Object enforceIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        String businessType = idempotent.businessType();
        String key = resolveKey(idempotent.key(), joinPoint);
        String requestHash = hashRequest(joinPoint.getArgs());
        Optional<IdempotencyRecord> existing = repository.findByBusinessTypeAndIdempotencyKey(businessType, key);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (!record.getRequestHash().equals(requestHash)) {
                throw new IdempotencyException("Idempotency key was reused with a different request");
            }
            if (record.getStatus() == IdempotencyStatus.COMPLETED) {
                return deserializeResponse(record, ((MethodSignature) joinPoint.getSignature()).getMethod());
            }
            if (record.getStatus() == IdempotencyStatus.PROCESSING) {
                throw new IdempotencyException("Idempotent request is already processing");
            }
        }

        IdempotencyRecord record = existing.orElseGet(IdempotencyRecord::new);
        LocalDateTime now = LocalDateTime.now();
        if (record.getId() == null) {
            record.setBusinessType(businessType);
            record.setIdempotencyKey(key);
            record.setRequestHash(requestHash);
            record.setCreatedAt(now);
            record.setExpiresAt(now.plusSeconds(idempotent.ttlSeconds()));
        }
        record.setStatus(IdempotencyStatus.PROCESSING);
        record.setUpdatedAt(now);
        repository.save(record);

        try {
            Object result = joinPoint.proceed();
            record.setResponseBody(serializeResponse(result));
            record.setResponseType(result != null ? result.getClass().getName() : null);
            record.setStatus(IdempotencyStatus.COMPLETED);
            record.setUpdatedAt(LocalDateTime.now());
            repository.save(record);
            return result;
        } catch (Throwable ex) {
            record.setStatus(IdempotencyStatus.FAILED);
            record.setUpdatedAt(LocalDateTime.now());
            repository.save(record);
            throw ex;
        }
    }

    private String resolveKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        if (keyExpression == null || keyExpression.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            return signature.getMethod().getDeclaringClass().getSimpleName() + "." + signature.getMethod().getName();
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            EvaluationContext context = new StandardEvaluationContext();
            Object[] args = joinPoint.getArgs();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
            for (int i = 0; i < args.length; i++) {
                context.setVariable("p" + i, args[i]);
                context.setVariable("a" + i, args[i]);
                if (paramNames != null && i < paramNames.length) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            Object resolved = parser.parseExpression(keyExpression).getValue(context);
            if (resolved == null || resolved.toString().isBlank()) {
                throw new IdempotencyException("Idempotency key must not be blank");
            }
            return resolved.toString();
        } catch (IdempotencyException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to resolve idempotency key expression '{}', using raw expression", keyExpression, ex);
            return keyExpression;
        }
    }

    private String hashRequest(Object[] args) throws JsonProcessingException {
        return sha256(objectMapper.writeValueAsString(args));
    }

    private String serializeResponse(Object result) throws JsonProcessingException {
        return result == null ? null : objectMapper.writeValueAsString(result);
    }

    private Object deserializeResponse(IdempotencyRecord record, Method method) throws JsonProcessingException {
        Class<?> returnType = method.getReturnType();
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType) || record.getResponseBody() == null) {
            return null;
        }
        if (String.class.equals(returnType)) {
            return objectMapper.readValue(record.getResponseBody(), String.class);
        }
        return objectMapper.readValue(record.getResponseBody(), returnType);
    }

    private String sha256(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }
}
