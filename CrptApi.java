package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {

    private final OkHttpClient client;
    private final Semaphore semaphore;
    private Instant lastRequestTime;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        // Настройка клиента HTTP с логированием запросов и ответов
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .build();

        // Инициализация семафора для ограничения запросов
        semaphore = new Semaphore(requestLimit);
        lastRequestTime = Instant.now();
    }

    public void createDocument(Document document, String signature) throws InterruptedException, IOException {
        // Преобразование документа в JSON
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(document);

        // Формирование запроса
        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url("https://ismp.crpt.ru/api/v3/lk/documents/create")
                .post(body)
                .addHeader("Authorization", "Bearer " + signature)
                .build();

        // Проверка, не превышен ли лимит запросов
        if (semaphore.tryAcquire(0, Duration.between(lastRequestTime, Instant.now()).toMillis(), TimeUnit.MILLISECONDS)) {
            // Лимит не превышен, выполняем запрос
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Ошибка при выполнении запроса: " + response.code());
                }
            }
        // Обновляем время последнего запроса
            lastRequestTime = Instant.now();

        } else {
            // Лимит превышен, блокируем запрос
            semaphore.acquire();

            // Повторно выполняем запрос
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Ошибка при выполнении запроса: " + response.code());
                }
            }
        }
    }

    public static class Document {
        // Поля документа
    }
}
