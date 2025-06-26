# LeeesVelocityQueue

A lightweight and performant **2b2t-style queue plugin for [Velocity](https://velocitypowered.com/)**.  
Designed for large servers with high traffic, **LeeesVelocityQueue** ensures orderly player login with support for priority, admin bypass, and a simple configuration.

---

## 🚀 Features

- ✅ **Join queue** system with customizable priorities  
- ⚙️ **Permission-based** access and control  
- 🧑‍💼 Admin bypass and management support  
- 🔧 Simple configuration and easy setup  
- 💨 Fast, efficient, and tailored for 2b2t-style servers

---

## 📦 Permissions

| Permission        | Description                            |
|-------------------|----------------------------------------|
| `lvq.bypass`      | Skip the queue entirely                |
| `lvq.admin`       | Allows queue management actions        |
| `lvq.priority`    | Grants access to the priority queue    |

---

## 🛠️ Building

Clone the repository and run:

```bash
mvn clean package
```

The final JAR will be located in the `target/` directory.

---

## 📁 Installation

1. Download the latest build.
2. Place the JAR in your **Velocity plugins folder**.
3. Restart your proxy server.
4. Configure `config.toml` as needed.

---

## 📃 Configuration

A sample configuration file will be generated on first run.  
You can customize queue messages, priority levels, and more.

---

## 🧠 Usage Example

Let regular users wait in queue, while trusted users with `lvq.priority` skip ahead:

```toml
# Example config.toml snippet
priority-roles = ["vip", "staff"]
```

---

## 🤝 Contributions

Pull requests are welcome!  
Feel free to fork the project and submit improvements or fixes.

---

## 📜 License

MIT License — feel free to use, modify, and share.
