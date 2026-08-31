DROP TABLE IF EXISTS stellar_category;
CREATE TABLE stellar_category (
    id            BIGINT   NOT NULL AUTO_INCREMENT, name VARCHAR(32) NOT NULL,
    type          TINYINT  NOT NULL DEFAULT 1,      sort INT NOT NULL DEFAULT 0,
    status        TINYINT  NOT NULL DEFAULT 1,      create_time DATETIME NOT NULL,
    create_user   BIGINT   NOT NULL,                update_time DATETIME NOT NULL,
    update_user   BIGINT   NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_name_type UNIQUE (name, type)
);

DROP TABLE IF EXISTS stellar_employee;
CREATE TABLE stellar_employee (
    id            BIGINT   NOT NULL AUTO_INCREMENT,  name VARCHAR(32) NOT NULL,
    username      VARCHAR(32) NOT NULL,              password VARCHAR(64) NOT NULL,
    phone         VARCHAR(11),                       sex VARCHAR(2),
    id_number     VARCHAR(18),                       role VARCHAR(32) NOT NULL DEFAULT 'employee',
    status        TINYINT  NOT NULL DEFAULT 1,       create_time DATETIME NOT NULL,
    create_user   BIGINT   NOT NULL,                 update_time DATETIME NOT NULL,
    update_user   BIGINT   NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_username UNIQUE (username)
);

DROP TABLE IF EXISTS stellar_user;
CREATE TABLE stellar_user (
    id            BIGINT   NOT NULL AUTO_INCREMENT,  email VARCHAR(64) NOT NULL,
    password      VARCHAR(64) NOT NULL,              nickname VARCHAR(32),
    phone         VARCHAR(20),                       avatar VARCHAR(255),
    role          VARCHAR(32) DEFAULT 'user',        status TINYINT NOT NULL DEFAULT 1,
    create_time   DATETIME NOT NULL,                 create_user BIGINT NOT NULL DEFAULT 0,
    update_time   DATETIME NOT NULL,                 update_user BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id), CONSTRAINT uk_email UNIQUE (email)
);

DROP TABLE IF EXISTS stellar_address_book;
CREATE TABLE stellar_address_book (
    id            BIGINT   NOT NULL AUTO_INCREMENT,  user_id BIGINT NOT NULL,
    consignee     VARCHAR(32) NOT NULL,              phone VARCHAR(20) NOT NULL,
    province      VARCHAR(32),                       city VARCHAR(32),
    district      VARCHAR(32),                       detail VARCHAR(200),
    is_default    TINYINT  DEFAULT 0,                create_time DATETIME NOT NULL,
    create_user   BIGINT   NOT NULL,                 update_time DATETIME NOT NULL,
    update_user   BIGINT   NOT NULL,                 PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stellar_spu;
CREATE TABLE stellar_spu (
    id BIGINT NOT NULL AUTO_INCREMENT, name VARCHAR(100) NOT NULL,
    sub_title VARCHAR(200), brand_id BIGINT, category_id BIGINT, category2_id BIGINT,
    description CLOB, description_md CLOB, main_image VARCHAR(500),
    sub_images CLOB,
    sale_count INT DEFAULT 0, comment_count INT DEFAULT 0,
    total_stock INT DEFAULT 0, sku_count INT DEFAULT 0,
    min_price DECIMAL(10,2) DEFAULT 0, max_price DECIMAL(10,2) DEFAULT 0,
    is_new TINYINT DEFAULT 0, is_hot TINYINT DEFAULT 0,
    sort INT DEFAULT 0, status TINYINT NOT NULL DEFAULT 1,
    on_shelf_time DATETIME, off_shelf_time DATETIME,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL, PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stellar_sku;
CREATE TABLE stellar_sku (
    id BIGINT NOT NULL AUTO_INCREMENT, spu_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL, specs VARCHAR(500), specs_json VARCHAR(1000),
    price DECIMAL(10,2) NOT NULL DEFAULT 0, original_price DECIMAL(10,2) DEFAULT 0,
    cost_price DECIMAL(10,2) DEFAULT 0,
    stock INT DEFAULT 0, warn_stock INT DEFAULT 10, version INT DEFAULT 0,
    weight_g INT DEFAULT 0, barcode VARCHAR(64),
    image VARCHAR(500), sort INT DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL, PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stellar_mall_order;
CREATE TABLE stellar_mall_order (
    id BIGINT NOT NULL AUTO_INCREMENT, order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL, address_id BIGINT,
    total_amount DECIMAL(10,2) NOT NULL DEFAULT 0, pay_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
    pay_method TINYINT, status TINYINT NOT NULL DEFAULT 1,
    address VARCHAR(500), consignee VARCHAR(50) NOT NULL DEFAULT '', phone VARCHAR(20) NOT NULL DEFAULT '',
    cancel_reason VARCHAR(500), pay_time DATETIME,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL,
    PRIMARY KEY (id), CONSTRAINT uk_order_no UNIQUE (order_no)
);

DROP TABLE IF EXISTS stellar_order_item;
CREATE TABLE stellar_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT, order_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
    spu_name VARCHAR(200), sku_name VARCHAR(200), image VARCHAR(500),
    price DECIMAL(10,2) NOT NULL DEFAULT 0, quantity INT NOT NULL DEFAULT 1,
    service_fee DECIMAL(10,2) DEFAULT 0,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL, PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stellar_cart;
CREATE TABLE stellar_cart (
    id BIGINT NOT NULL AUTO_INCREMENT, user_id BIGINT NOT NULL,
    spu_id BIGINT NOT NULL, sku_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1, checked TINYINT DEFAULT 1,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL, PRIMARY KEY (id)
);

DROP TABLE IF EXISTS stellar_rag_sync_outbox;
CREATE TABLE stellar_rag_sync_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT, biz_type VARCHAR(16) NOT NULL,
    biz_id BIGINT NOT NULL, op_type VARCHAR(32) NOT NULL DEFAULT 'SAVE',
    synced TINYINT NOT NULL DEFAULT 0, failed TINYINT NOT NULL DEFAULT 0,
    retry_count INT NOT NULL DEFAULT 0, max_attempt INT NOT NULL DEFAULT 3,
    last_try_time DATETIME, last_error_msg VARCHAR(1000),
    payload_json CLOB,
    create_time DATETIME NOT NULL, create_user BIGINT NOT NULL,
    update_time DATETIME NOT NULL, update_user BIGINT NOT NULL, PRIMARY KEY (id)
);
