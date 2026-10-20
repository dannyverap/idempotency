# WRDA Idempotency Demo

Demo de idempotencia HTTP para Quarkus 3.33 y Java 21. Usa CDI, Hibernate ORM/Panache y una base de datos relacional. La demo corre con H2 en memoria; no usa Redis.

## Qué garantiza

`@WrdaManagedEndpoint` combina el manejo de errores con `@WrdaIdempotency`. El interceptor reclama una clave antes de ejecutar el endpoint. Tras una respuesta 2xx, persiste una instantánea HTTP cifrada con AES-256-GCM en la tabla `idempotency_keys`. Un reintento con la misma clave, identidad y petición devuelve el mismo status, cuerpo y encabezados de respuesta permitidos, sin ejecutar de nuevo el código de negocio ni volver a leer tablas de dominio.

Esto también funciona si la respuesta representa varias tablas. Por ejemplo, `POST /api/orders/with-payment` crea una orden y un pago, pero el replay no tiene que descubrir ni consultar esas tablas: recupera la instantánea original. La reserva `IN_PROGRESS`, los cambios de negocio y el estado `COMPLETED` se coordinan en la misma base. Los endpoints de escritura tienen `@Transactional`; el interceptor verifica que exista una transacción JTA activa y el guardado de `COMPLETED` se une a ella. Si la transacción falla, tanto el cambio de negocio como la instantánea se revierten.

La tabla no almacena la clave del cliente en claro. La clave y la huella de la solicitud se derivan con HMAC-SHA-256. La huella considera identidad/tenant, método, ruta, query y parámetros del endpoint serializados. Reutilizar una clave con otra huella responde `422`; una solicitud idéntica que continúa en proceso responde `409` con `Retry-After`.

## Protección de datos y secretos

La instantánea completa —status, cuerpo y encabezados seleccionados— se cifra con AES-256-GCM. El identificador derivado de la clave se usa como AAD para impedir que el ciphertext se mueva a otro registro sin ser detectado. El cuerpo máximo es 1 MiB por defecto. Solo se aceptan respuestas JSON y texto no streaming. Se guardan encabezados permitidos (`Location`, `Cache-Control`, `ETag`, `Last-Modified`, `Expires`, `Content-Language` y `Vary`); no se guardan `Set-Cookie`, `Authorization` ni encabezados arbitrarios.

Las claves no están en el repositorio. El runtime requiere estas variables, que en producción deben venir del secret manager aprobado:

- `WRDA_IDEMPOTENCY_ENCRYPTION_KEYS`: key ring en formato `id:base64,id-antigua:base64`.
- `WRDA_IDEMPOTENCY_ENCRYPTION_CURRENT_KEY_ID`: ID usado para cifrar escrituras nuevas.
- `WRDA_IDEMPOTENCY_HMAC_KEY`: secreto Base64 de al menos 32 bytes para la clave derivada y la huella.
- `WRDA_IDEMPOTENCY_REQUIRE_AUTHENTICATED_IDENTITY`: activar `true` en producción.

Generación local de secretos efímeros (no reutilizar en despliegues):

```bash
export WRDA_IDEMPOTENCY_ENCRYPTION_KEYS="v1:$(openssl rand -base64 32 | tr -d '\n')"
export WRDA_IDEMPOTENCY_ENCRYPTION_CURRENT_KEY_ID=v1
export WRDA_IDEMPOTENCY_HMAC_KEY="$(openssl rand -base64 32 | tr -d '\n')"
export WRDA_IDEMPOTENCY_REQUIRE_AUTHENTICATED_IDENTITY=false
mvn quarkus:dev
```

La identidad autenticada se incorpora al ámbito. Si existe el atributo `tenant_id` en `SecurityIdentity`, también se incorpora. El valor de `require-authenticated-identity` queda desactivado por defecto para que la demo local sin proveedor de autenticación funcione. En un despliegue bancario debe activarse, integrar el proveedor de identidad real y verificar que la identidad/tenant representan al cliente correcto. No se debe usar un header controlado por el usuario como ámbito de tenant.

La rotación de cifrado es por ID: se puede cifrar con una clave nueva y mantener las claves antiguas en el key ring hasta que venzan todos los registros cifrados con ellas. No quitar una clave histórica antes de expirar/retirar los registros correspondientes. El secreto HMAC debe permanecer estable durante la vida máxima de las claves guardadas; reemplazarlo sin una migración coordinada hace que las claves existentes ya no sean localizables y puede permitir repetir operaciones. La rotación HMAC requiere un procedimiento separado y validado.

Esta versión también cambia la derivación de claves anteriores de SHA-256 sin clave a HMAC y reemplaza referencias de recursos por snapshots cifrados. Las filas COMPLETED creadas por la versión anterior no contienen un snapshot descifrable, y sus hashes no coinciden con el formato nuevo. Antes de actualizar un entorno con datos persistidos, drenar las operaciones en curso y esperar el TTL anterior o ejecutar una migración/corte aprobado que garantice que un reintento antiguo no vuelva a ejecutar negocio. No desplegar directamente sobre claves aún vigentes sin resolver esa compatibilidad.

El cifrado de aplicación no sustituye el cifrado de disco/base de datos, TLS, control de acceso, auditoría, backups cifrados, retención ni clasificación de datos del banco. La instantánea puede incluir información sensible de la respuesta: aplicar permisos mínimos a la tabla y backups, revisar qué APIs son elegibles, reducir el TTL al mínimo aprobado y no almacenar tokens/cookies en el cuerpo.

## Fallos y recuperación operativa

Los estados distintos de 2xx y las excepciones no se guardan como resultado replayable. Un estado no exitoso marca rollback de la transacción de negocio y libera la reserva. Una excepción también revierte negocio y libera la clave cuando el proceso sigue disponible.

No se retoma automáticamente una fila `IN_PROGRESS` por antigüedad. Hacerlo podría volver a ejecutar una operación que todavía está viva y producir efectos duplicados. Si el proceso muere, la clave queda bloqueada y devuelve `409` hasta recuperación operativa. Antes de liberar una reserva huérfana, el procedimiento operativo debe verificar en la base de negocio si la operación se confirmó; no borrar a ciegas. Es un comportamiento deliberadamente *fail closed*.

La reserva se confirma en una transacción corta antes del negocio. En contraste, los cambios de negocio y el paso a `COMPLETED` comparten la transacción del endpoint, en la misma base. Para efectos externos no transaccionales (por ejemplo, un proveedor de pagos), la transacción local no garantiza exactly-once. Usar una clave idempotente del proveedor y/o patrón outbox con evento agnóstico del dominio; no acoplar el dominio a Kafka u otro broker concreto.

## Arquitectura

- `domain`: entidades/validaciones de negocio, sin dependencias de idempotencia ni eventos concretos.
- `application/idempotency`: servicio de aplicación y puerto `IdempotencyStore`.
- `infrastructure/idempotency/http`: anotación CDI, captura HTTP, fingerprint y short-circuit del replay.
- `infrastructure/idempotency/security`: HMAC y cifrado AES-GCM.
- `infrastructure/idempotency/persistence`: Panache, SQL y limpieza de claves COMPLETED vencidas.
- `infrastructure/orders`: ejemplo HTTP y persistencia del dominio.

La implementación actual de reclamo atómico usa `MERGE ... WHEN NOT MATCHED`, propia de H2 en esta demo. Antes de integrar otro motor SQL, implementar y probar el puerto `IdempotencyStore` con la primitiva atómica adecuada a ese motor, revisar aislamiento/bloqueos y reemplazar el DDL de H2 por migraciones versionadas (por ejemplo, Flyway/Liquibase). No ejecutar este esquema `schema.sql` sin adaptación contra la base de datos bancaria.

## Ejecución y comprobación manual

```bash
mvn test
mvn quarkus:dev
```

Crear una orden:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: order-001' \
  -d '{"customerId":"C-100","amount":150.50}'
```

Repetir exactamente esa llamada devuelve la misma respuesta con `Idempotency-Replayed: true`. Cambiar el payload y conservar la clave devuelve `422`. Mientras la primera llamada esté activa, una petición simultánea devuelve `409`. La ruta `POST /api/orders/with-payment` prueba replay de respuesta compuesta.

Las pruebas de integración en `src/test` cubren replay byte-a-byte, rechazo de payload distinto, concurrencia, liberación luego de una falla y replay de orden+pago. Los secretos deterministas de `src/test/resources/application.properties` son únicamente para las pruebas automatizadas.

## Alcance de producción

Este repositorio es una demo, no un artefacto certificado ni una solución lista para desplegar en un banco. Antes de producción se requieren revisión de seguridad, decisión aprobada sobre clasificación/retención de snapshots, gestión y rotación de claves, migraciones de esquema, base relacional y prueba de concurrencia del motor elegido, autenticación/tenant, observabilidad sin PII y procedimientos operativos para registros `IN_PROGRESS` huérfanos.
