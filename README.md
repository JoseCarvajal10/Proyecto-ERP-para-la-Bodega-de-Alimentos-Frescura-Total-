# ERP para la Bodega de Alimentos "Frescura Total"

## Descripción

"Frescura Total" es un centro de distribución que conecta productores agrícolas con restaurantes,
supermercados y tiendas. Su operación en hojas de cálculo y papel generaba mermas por vencimiento,
ventas de productos que ya no estaban en stock, imposibilidad de rastrear un lote hasta su proveedor
y facturas hechas a mano. Este proyecto es un ERP de consola, hecho en Java con arquitectura MVC por
componentes y MySQL, que controla el inventario por lotes con sus estados y alertas de vencimiento,
gestiona proveedores y compras, vende con la estrategia FEFO aplicando promociones automáticas a los
lotes próximos a vencer, genera la factura electrónica visual y hace seguimiento de los despachos
hasta la entrega.

## Autor

- Jose Enrique Carvajal Orduz

## Requerimientos previos

- Java JDK 17 o superior
- Apache NetBeans IDE 12 o superior (incluye Maven)
- MySQL 8 (instalado en el equipo o en un contenedor Docker)
- Git

## Configuración de la base de datos

1. Inicia tu servidor MySQL 8. Si usas Docker, por ejemplo:

   ```
   docker run --name mi-mysql -e MYSQL_ROOT_PASSWORD=tu_clave -p 3306:3306 -d mysql:8.0
   ```

2. Ejecuta el script **`docs/database.sql`** con tu cliente SQL favorito (MySQL Workbench, DBeaver o
   la línea de comandos). Ejecútalo completo, como script y no sentencia por sentencia:

   ```
   mysql -u root -p --default-character-set=utf8mb4 < docs/database.sql
   ```

   El script borra y vuelve a crear la base `erp_frescura_total`. Tiene dos secciones:
   **DDL** (creación de las 22 tablas) y **DML** (datos de prueba: 55 proveedores, 60 productos,
   55 clientes, 60 lotes, 55 ventas con 134 líneas, 55 facturas, rutas, mermas e inspecciones).

3. Configura la conexión mediante variables de entorno (no guardes contraseñas en Git):

   ```text
   ERP_DB_HOST=localhost
   ERP_DB_PORT=3306
   ERP_DB_NAME=erp_frescura_total
   ERP_DB_USER=root
   ERP_DB_PASSWORD=tu_clave
   ```

   En PowerShell, por ejemplo: `$env:ERP_DB_PASSWORD = "tu_clave"`. Las demás variables tienen
   valores locales predeterminados (`localhost`, `3306`, `erp_frescura_total`, `root` y contraseña vacía).

## Instalación y ejecución

1. Clona el repositorio:

   ```
   git clone <URL-del-repositorio>
   ```

2. En Apache NetBeans: **File > Open Project** y selecciona la carpeta `frescura-total-erp`.
3. Compila: clic derecho sobre el proyecto y **Clean and Build** (o menú **Run > Clean and Build Project**).
   Maven descarga solo las librerías (conector de MySQL y ZXing para el código QR).
4. Ejecuta: **Run > Run Project** o la tecla **F6**. La aplicación se usa desde la consola de NetBeans.
5. (Opcional) Desde la terminal. Con Maven el `.jar` queda en `target/` (no en `dist/`, que es de los
   proyectos Ant):

   ```
   java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar target/frescura-total-erp-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

Si en la consola de NetBeans se ven mal las tildes, en **Properties > Run > VM Options** escribe
`-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`.

### Logs

La aplicación registra el inicio y cierre, errores de conexión y fallos de operaciones en
`logs/frescura-total-0.log` (rotación automática de hasta tres archivos). La carpeta `logs/` está
excluida de Git para no versionar archivos de ejecución.

### Subir el proyecto a Git

Desde la carpeta raíz del proyecto, inicializa el repositorio local y crea el primer commit:

```bash
git init
git add .
git status
git commit -m "Entrega inicial del ERP Frescura Total"
```

Luego crea un repositorio vacío en GitHub/GitLab y conecta su URL:

```bash
git remote add origin <URL-del-repositorio>
git branch -M main
git push -u origin main
```

Antes de subir, revisa `git status` y confirma que no aparezcan contraseñas, `target/`, `logs/` ni
facturas generadas. El script SQL reinicia la base indicada al ejecutarse: úsalo solo en una instancia
de desarrollo o evaluación.

### Cómo se usa

Al arrancar, el sistema comprueba la base de datos, pregunta el rol y el nombre de quien lo usa y
ejecuta el proceso diario que revisa los vencimientos. Cada rol ve solo sus módulos:

| Rol | Módulos | Puede hacer |
|---|---|---|
| Administrador | Todos | Todo |
| Operario de bodega | Inventario, Proveedores | Recibir mercancía, cuarentena, mermas, proveedores |
| Inspector de calidad | Inventario | Cuarentena y decidir inspecciones (aprobar/rechazar) |
| Encargado de ventas | Ventas | Clientes, ventas, facturas, pasar ventas a preparación |
| Encargado de logística | Logística | Panel de despachos, rutas, entregas |

Las facturas se guardan como imagen PNG en la carpeta `invoices/` (junto a donde se ejecuta el programa).

## Estructura del proyecto

El proyecto sigue MVC **por componentes**: cada módulo del negocio tiene sus propios paquetes
`model`, `view` y `controller`.

```
frescura-total-erp/
├── pom.xml
├── README.md
├── docs/
│   ├── database.sql          (DDL + datos de prueba)
│   ├── diagrama_er.png       (diagrama entidad-relación)
│   └── diagrama_clases.png   (diagrama de clases UML)
└── src/main/java/com/frescuratotal/erp/
    ├── Main.java                    punto de entrada
    ├── ApplicationController.java   usuario, rol, proceso diario y menú principal
    ├── common/                      conexión a BD, configuración, lectura de consola, roles
    ├── inventory/                   lotes, estados, trazabilidad, mermas, cuarentena, inspección
    │   ├── controller/  model/  view/
    ├── suppliers/                   directorio, catálogo, historial y fiabilidad
    │   ├── controller/  model/  view/
    ├── sales/                       clientes, venta FEFO, promociones, factura PNG
    │   ├── controller/  model/  view/
    └── logistics/                   panel de despachos, rutas, entregas
        ├── controller/  model/  view/
```

Todo el código está en inglés y sigue las convenciones de Java. Los nombres de las tablas y columnas
de la base de datos están en español porque vienen del script entregado en el enunciado.

## Funcionalidades por módulo

- **Inventario y calidad:** recepción de mercancía con generación de número de lote único; consulta
  por lote, ubicación (por ejemplo A-05) y estado; trazabilidad completa (proveedor de cada lote y
  clientes que recibieron productos de él); estados Available, Near Expiry, Expired e In Quarantine;
  proceso automático de vencimientos (7 días o menos pasa a Near Expiry, vencidos quedan bloqueados);
  registro de mermas; cuarentena de lotes completos e inspección (Approved devuelve el lote a la
  venta, Rejected convierte todo el stock en merma).
- **Proveedores y compras:** directorio, catálogo de productos por proveedor, historial de entregas
  (cada lote queda ligado a su proveedor) y datos de fiabilidad (porcentaje de merma e inspecciones
  rechazadas).
- **Ventas y facturación:** clientes mayoristas y minoristas; el pedido usa FEFO y reparte la cantidad
  entre varios lotes si hace falta; los lotes Near Expiry reciben el descuento configurado en la tabla
  `promociones` (50 %); al confirmar se descuenta el stock automáticamente y se genera la factura PNG
  con NIT de la empresa, datos del cliente, detalle, impuestos, CUFE y QR simulados.
- **Logística:** panel con las ventas In Preparation, creación de rutas con vehículo y conductor,
  salida de la ruta (pedidos In Transit) y registro de entregas (Delivered); la ruta se completa sola
  cuando se entrega el último pedido.

## Principios y patrones de diseño

**MVC por componentes.** Cada módulo separa tres responsabilidades. La *vista* (por ejemplo
`InventoryView`) solo imprime y lee datos de la consola, sin SQL ni reglas de negocio. El
*controlador* (`InventoryController`) interpreta lo que elige el usuario, valida permisos del rol y
decide qué pedir al modelo. El *modelo* contiene las entidades (`Lot`, `Sale`) y el acceso a datos.
Lo elegí para poder cambiar una parte sin romper las demás: por ejemplo, cambiar la consola por una
interfaz gráfica solo obligaría a reescribir las vistas.

**Patrón DAO (Data Access Object).** Todo el SQL vive en clases `*Dao` (`LotDao`, `SaleDao`,
`LogisticsDao`...), que devuelven objetos Java y no filas. Así el resto del programa no sabe cómo se
guardan los datos. Las operaciones que modifican varias tablas a la vez (confirmar una venta, inspeccionar
un lote, crear una ruta) se hacen en una sola transacción, de modo que si algo falla no queda nada a
medias, por ejemplo una venta guardada sin haber descontado el stock.

**Patrón Singleton.** `DatabaseConnection` tiene una única instancia de la que todos obtienen
conexiones mediante `getInstance()`. Su constructor es privado, y así hay un solo punto donde se
configura cómo se conecta el sistema a MySQL.

**Principio de responsabilidad única (SRP).** Las clases con lógica propia hacen una sola cosa:
`FefoAllocator` decide qué lotes usar en una venta, `TaxPolicy` calcula el IVA según la categoría,
`InvoiceRenderer` solo dibuja la factura y `ConsoleReader` solo valida lo que se escribe. Si cambia la
regla del impuesto o el diseño de la factura, se toca una sola clase.

**Tipos seguros para los estados.** Los estados de lotes, ventas y rutas son enums (`LotStatus`,
`SaleStatus`, `RouteStatus`) en lugar de textos sueltos, para que el compilador detecte los errores
de escritura. Las clases de datos sin comportamiento (`Product`, `Supplier`, `Invoice`...) son
`record`, que son inmutables.

## Decisiones y supuestos

- Se pueden vender lotes Available y Near Expiry (estos últimos con descuento). Los vencidos y los
  que están en cuarentena quedan bloqueados.
- Impuestos simplificados: los alimentos frescos no llevan IVA y las bebidas y enlatados llevan 19 %.
- La factura es una representación gráfica simulada con fines académicos: el CUFE se calcula con
  SHA-384 y el QR contiene un enlace simulado.
- Los roles no tienen contraseña; sirven para mostrar la separación de funciones.
- Un lote rechazado en inspección queda con cantidad 0 y su merma registrada, vinculada a la inspección.

## Diagramas

- Diagrama entidad-relación: [`docs/diagrama_er.png`](docs/diagrama_er.png)
- Diagrama de clases UML: [`docs/diagrama_clases.png`](docs/diagrama_clases.png)
