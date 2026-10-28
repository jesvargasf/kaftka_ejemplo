# Kafka básico — Docker y un microservicio

Esta guía levanta Kafka en Docker y un programa Spring Boot que escribe un pedido y lo lee. Sirve para ver el topic, la partición y el offset. No incluye Kafka Streams ni una base de datos.

| Programa | Puerto | Para qué |
|---|---|---|
| ZooKeeper | 2181 | Kafka de esta imagen lo usa para coordinarse. No lo abres |
| Kafka | 9092 | El broker. El microservicio habla con este puerto |
| Kafka UI | 8080 | La página para ver el topic, las particiones y los offsets |
| Microservicio | 8888 | `POST /pedidos` |

Kafka UI usa el puerto 8080. Si todavía tienes corriendo el demo de Rabbit en ese puerto, apágalo antes.

---

## 1. Levantar Kafka

En esta carpeta, con Docker Desktop ya en marcha:

```bash
docker compose up -d
```

Espera unos treinta segundos. Abre [http://localhost:8080](http://localhost:8080). En **Topics** todavía puede no estar `pedidos`: lo crea el microservicio al arrancar.

Para apagarlo:

```bash
docker compose down
```

---

## 2. Levantar el microservicio

Hace falta Java 17 y Maven.

```bash
mvn spring-boot:run
```

Al partir, Spring crea el topic `pedidos` con 3 particiones. En Kafka UI recarga **Topics** y ábrelo. Tienen que verse las particiones 0, 1 y 2.

El programa hace dos cosas:

- `POST /pedidos` escribe en el topic. La clave del mensaje es el nombre del cliente.
- Un listener del grupo `cobros` lee ese topic y escribe en la consola la partición y el offset.

---

## 3. Probar

En otra terminal, o con la colección `postman/DSY1107-demo-kafka.postman_collection.json`:

```bash
curl -X POST http://localhost:8888/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Juan","monto":100}'

curl -X POST http://localhost:8888/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Juan","monto":50}'

curl -X POST http://localhost:8888/pedidos \
  -H "Content-Type: application/json" \
  -d '{"cliente":"Maria","monto":80}'
```

En la consola del microservicio sale algo de esta forma:

```text
[PUBLICA] topic=pedidos key=Juan valor=Juan compro 100
[LEE] grupo=cobros particion=2 offset=0 key=Juan valor=Juan compro 100
[PUBLICA] topic=pedidos key=Juan valor=Juan compro 50
[LEE] grupo=cobros particion=2 offset=1 key=Juan valor=Juan compro 50
[PUBLICA] topic=pedidos key=Maria valor=Maria compro 80
[LEE] grupo=cobros particion=0 offset=0 key=Maria valor=Maria compro 80
```

Los números de partición pueden ser otros. Lo que no cambia es la regla: los dos de Juan caen en la misma partición, y el offset de esa partición sube de 0 a 1. María puede caer en otra. Su offset 0 es otra página, de otro tomo.

En Kafka UI, abre el topic `pedidos` y entra a los mensajes. Vas a ver la misma partición, la misma clave y el mismo offset que imprimió la consola. El mensaje sigue ahí después de leído. El grupo `cobros` solo avanzó su marca.

---

## 4. Qué hace cada clase

El `POST` entra por el controlador, el servicio publica en Kafka y el consumidor lee. El controlador no conoce el topic.

| Paquete | Clase | Rol |
|---|---|---|
| `model` | `Pedido` | El dato: cliente y monto |
| `controller` | `PedidoController` | Recibe el HTTP y responde. No habla con Kafka |
| `service` | `PedidoService` | Valida y publica. Esta línea es el productor: `kafkaTemplate.send("pedidos", cliente, texto)` |
| `consumer` | `CobrosListener` | Lee el topic en el grupo `cobros` e imprime partición y offset |
| `config` | `KafkaConfig` | Crea el topic al arrancar: `pedidos`, 3 particiones, 1 réplica |

El segundo dato de `send` es la clave. Kafka usa esa clave para elegir la partición. La misma clave cae siempre en la misma partición, y ahí los pedidos de Juan quedan en orden.

`@KafkaListener(..., groupId = "cobros")` anota al consumidor en el grupo `cobros`. El offset que imprime es el número de página dentro de esa partición.

En `application.yml`, `bootstrap-servers: localhost:9092` es la dirección del broker. `auto-offset-reset: earliest` dice: si este grupo nunca leyó el topic, empieza desde el primer mensaje.

---

## 5. Qué estás viendo, y qué no

Estás viendo el cuaderno: se escribe, se lee, y la página no se arranca. Un solo grupo (`cobros`) recorre las tres particiones. Como hay un solo listener, ese proceso lee las tres.

No estás viendo todavía un segundo grupo (fraude leyendo los mismos pedidos), ni Kafka Streams, ni el cajón de conteos, ni la tabla del tablero. Esos pasos se apoyan en esto: primero el evento tiene que quedar en el topic.
