## 🐘 Oracle Database 19c on (M1/M2/M3 Max)

Run Oracle Database 19c Enterprise Edition in a Docker container on M1, M2, M3 Max Macs.

---

### ✅ Prerequisites

1. **Clone the Oracle Docker Images Repository**

   ```bash
   git clone https://github.com/oracle/docker-images.git
    ````

2. **Download Oracle Database 19c for LINUX ARM (aarch64)**
   🔗 [Oracle Database Downloads](https://www.oracle.com/database/technologies/oracle-database-software-downloads.html)

    * Requires an Oracle account (free)
    * Download the version for **Linux ARM (aarch64)**
    * Place the downloaded `.zip` file in:

      ```
      docker-images/OracleDatabase/SingleInstance/dockerfiles/19.3.0
      ```

3. **Navigate to the Dockerfiles directory**

   ```bash
   cd docker-images/OracleDatabase/SingleInstance/dockerfiles
   ```

4. **Build the Oracle Database Docker image**

   ```bash
   ./buildContainerImage.sh -v 19.3.0 -e
   ```

5. **Run the Oracle container**

   ```bash
   docker run -d --name oracle19 -e ORACLE_PWD=mbpassword -p 1521:1521 oracle/database:19.3.0-ee
   ```
   **or Run the Oracle container and initialize schema**

   ```bash
   ./init_oracle_db.sh
   ```

---

### 🔌 Test Oracle Database Connection

#### 🗄️ Connection Details for CDB

* **Host:** `localhost`
* **Port:** `1521`
* **Service name:** `ORCLCDB`
* **Username:** `SYS as SYSDBA`
* **Password:** `mbpassword`

---

#### 🧩 Connection Details for PDB

* **Host:** `localhost`
* **Port:** `1521`
* **Service name:** `ORCLPDB`
* **Username:** `SYS as SYSDBA` or `myapp_user` as User & Password
* **Password:** `mbpassword` or `myapp_password`

---

### 📚 References

* 📺 [How to Install Oracle on an M1/M2 Mac (Finally)](https://www.youtube.com/watch?v=uxvoMhkKUPE)
* 📘 [Oracle Docker GitHub Repository](https://github.com/oracle/docker-images)

---
