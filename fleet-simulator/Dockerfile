FROM python:3.13-alpine
RUN pip install --no-cache-dir paho-mqtt==2.1.0
WORKDIR /simulator
COPY simulator.py .
CMD ["python", "-u", "simulator.py"]
