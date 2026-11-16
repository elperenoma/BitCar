FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY . .
RUN mkdir -p out && find . -name "*.java" > sources.txt && javac -encoding UTF-8 -d out @sources.txt
CMD ["j