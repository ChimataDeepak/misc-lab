FROM python:3.12-slim
WORKDIR /app
COPY pyproject.toml ./
COPY receipt_insights ./receipt_insights
RUN pip install --no-cache-dir .
USER nobody
ENV RECEIPTS_BASE_URL=http://host.docker.internal:8090
EXPOSE 8000
CMD ["uvicorn", "receipt_insights.app:app", "--host", "0.0.0.0", "--port", "8000"]
