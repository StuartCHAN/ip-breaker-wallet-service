.PHONY: up down logs verify run
up:
	docker compose up --build -d
down:
	docker compose down
logs:
	docker compose logs -f app
verify:
	./mvnw -B -ntp verify
run:
	./mvnw -pl patex-wallet-bootstrap -am spring-boot:run

