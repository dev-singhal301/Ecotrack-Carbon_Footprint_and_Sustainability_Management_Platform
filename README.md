<div align= "center">

<h2>Powered-Carbon-Footprint-Sustainability-Management-Platform</h2>
<div align="center">
  <a href="https://github.com/Springboard-mentor-4721/Ecotrack-Group-2/tree/milestone_4" target="_blank">
    <img width="140" alt="logo" src="https://github.com/user-attachments/assets/e098ed9a-b323-4bcc-a071-97138863d7f0" />
  </a>
  <h2>EcoTrack</h2>
  <p><b>Powered Carbon Footprint & Sustainability Management Platform</b></p>
</div>

![](https://img.shields.io/badge/Angular-DD0031?style=for-the-badge&logo=angular&logoColor=white)
![](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![](https://img.shields.io/badge/TypeScript-007ACC?style=for-the-badge&logo=typescript&logoColor=white)
![](https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![](https://img.shields.io/badge/JWT-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![](https://img.shields.io/badge/Google_OAuth2-4285F4?style=for-the-badge&logo=google&logoColor=white)
![](https://img.shields.io/badge/Google_Gemini-8E75B2?style=for-the-badge&logo=googlegemini&logoColor=white)

</div>

## 💡 Overview

EcoTrack is a carbon footprint tracking platform built for individuals who want to understand and reduce their environmental impact. Users log everyday activities — transportation, electricity, food, waste, water, and fuel — and EcoTrack converts them into a real, trackable carbon footprint.

> *"Keep tracking your everyday habits to lower your carbon footprint."*

Beyond tracking, EcoTrack turns sustainability into something people stick with: users set personal goals, join community challenges (like tree planting, park cleanups, or cycling instead of driving), and earn XP as they go. The XP, levels, and leaderboard exist mainly as a short-term motivator — a way to make consistent small effort feel rewarding right now, while the real payoff is the long-term, collective impact of everyone's habits adding up.

## ✨ Features

- **🔐 Secure Authentication** — Email/password login, Google OAuth2 sign-in, and JWT-based sessions, with password reset via email.
- **📊 Carbon Footprint Tracking** — Log daily activities across six categories (Transportation, Electricity, Food, Waste, Water, Fuel) and see calculated emissions instantly.
- **🎯 Personal Goals** — Set reduction targets and track progress with visual completion bars.
- **🏆 Community Challenges** — Join admin-created challenges (e.g. cycling, recycling, planting trees) with progress tracking and completion rewards.
- **🧠 AI-Powered Tips** — Personalized recommendations and goal insights generated via Google Gemini, with a rule-based fallback engine when the AI service is slow or unavailable.
- **📈 Interactive Dashboards** — Visual breakdowns of emissions by category, daily/weekly trends, and XP history via Chart.js.
- **🥇 Leaderboard & Gamification** — XP, levels, and badges (level milestones, leaderboard podium finishes, and challenge completions) to keep users engaged.
- **🪪 Eco Identity** — A combined profile and account settings page: edit name/avatar, view lifetime stats, and manage your account.
- **📄 Data Export** — Download your full activity history as PDF (with visual charts), Excel, or CSV.
- **🔔 Notifications** — In-app alerts for goal progress, challenge updates, and account activity.
- **🛠️ Admin Portal** — Admins can view platform-wide analytics, manage users (view/deactivate/reactivate), and create, edit, or cancel challenges.
- **🔒 Privacy-Conscious Account Deletion** — Soft-delete flow: users can request account deletion, which deactivates the account and queues it for a manual, permanent purge — with a window to change their mind and contact support to reactivate.
- **📱 Responsive Design** — Fully usable on desktop and mobile.

## 👩‍💻 Tech Stack

### Backend
- **Java 21**
- **Spring Boot 4.1.0**
- Spring Data JPA
- Spring Security
- Spring Web MVC
- Spring Validation
- Spring Mail
- **Database:** PostgreSQL (via Spring Boot JDBC/BOM)
- **Authentication:** JWT (`jjwt-api`, `jjwt-impl`, `jjwt-jackson` v0.12.6), Google OAuth2 (`google-api-client` v2.7.0)
- **AI Integration:** Google Gemini via the Google GenAI Java SDK (`google-genai` v1.4.1, `com.google.genai`) — powers AI-enhanced recommendations and goal insights, with a rule-based engine as fallback
- **Reports & Export:**
  - OpenPDF v1.3.30 (PDF generation)
  - Apache POI + POI-OOXML v5.3.0 (Excel export)
  - JFreeChart v1.5.4 (charts embedded in PDF reports)
- **Build Tool:** Maven

### Frontend
- **Angular** ^21.2.0 (core, common, forms, router, compiler, platform-browser)
- Angular CLI ^21.2.18
- **TypeScript** ~5.9.2
- RxJS ~7.8.0
- Chart.js ^4.5.1 with ng2-charts ^6.0.1
- **Testing:** Vitest ^4.0.8
- **Package Manager:** npm 11.6.2

## 📦 Getting Started

To get a local copy of this project up and running, follow these steps.

### 🚀 Prerequisites

- **Java 21** (JDK)
- **Node.js** and **npm** (compatible with Angular ^21.2.0)
- **Maven** (or use the included `mvnw` wrapper — no separate install needed)
- **PostgreSQL** running locally, with an empty database created (e.g. `ecotrack_db`)
- A **Google Cloud OAuth2 Client ID** (for Google Sign-In)
- A **Gemini API key** (optional — see [AI Tips setup](#-ai-tips-setup) below)

### 🛠️ Installation

1. **Clone the repository:**

   ```bash
   git clone https://github.com/Springboard-mentor-4721/Ecotrack-Group-2
   cd Ecotrack-Group-2
   ```

2. **Configure the backend:**

   In `ecotrack-backend/src/main/resources/application.properties`, set the following:

   ```properties
   # Application Name
   spring.application.name=ecotrack-backend

   # Database Initialization
   spring.sql.init.mode=always
   spring.jpa.defer-datasource-initialization=false

   # Database Configuration
   spring.datasource.url=jdbc:postgresql://localhost:5432/ecotrack_db
   spring.datasource.username=postgres
   spring.datasource.password=your_postgres_password

   # Security & Authentication
   jwt.secret=your_super_secret_jwt_signing_key
   google.client.id=your_google_oauth_client_id.apps.googleusercontent.com

   # Email Service (Password Reset)
   spring.mail.host=smtp.gmail.com
   spring.mail.port=587
   spring.mail.username=your_email@gmail.com
   spring.mail.password=your_gmail_app_password
   app.frontend.base-url=http://localhost:4200

   # AI Recommendation Engine
   gemini.api.key=your_gemini_api_key
   gemini.model=gemini-flash-latest
   ```

   > The database schema is created automatically on startup (`spring.sql.init.mode=always`) — no manual SQL setup required, as long as the target database already exists and is reachable.

3. **Configure the frontend:**

   In `ecotrack-frontend/src/environments/environment.ts`:

   ```typescript
   export const environment = {
     production: false,
     apiUrl: 'http://localhost:8080/'
   };
   ```

   You'll also need to set your Google OAuth2 Client ID in the frontend auth component to match the one configured on the backend.

4. **Run the backend:**

   ```bash
   cd ecotrack-backend
   ./mvnw spring-boot:run
   ```

5. **Run the frontend:**

   ```bash
   cd ecotrack-frontend
   ng serve
   ```

   Open [http://localhost:4200](http://localhost:4200) to view the app in your browser.

### 🤖 AI Tips Setup (Optional)

EcoTrack's AI Tips feature uses **Google Gemini** to generate personalized recommendations and goal insights. If no key is configured, or the service is slow to respond, a built-in rule-based engine provides fallback recommendations instead — so this step is optional.

1. Get a free API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Add it to `application.properties`:

   ```properties
   gemini.api.key=YOUR_KEY_HERE
   gemini.model=gemini-flash-latest
   ```

## 📸 Preview

<div align="center">

**Login**
<br>
<img src="<img width="1917" height="1031" alt="login" src="https://github.com/user-attachments/assets/225de9db-fe82-4f25-906a-8fd3d4430db8" />" width="80%">

<br><br>

**Dashboard**
<br>
<img src="<img width="1917" height="1030" alt="dashboard" src="https://github.com/user-attachments/assets/bf17e5b1-ecc0-4072-a420-a4f78a6acabe" />" width="80%">

<br><br>

**Tracking**
<br>
<img src="<img width="1917" height="1032" alt="tracking" src="https://github.com/user-attachments/assets/cfce62c6-3476-4e62-a303-8df3bf5bc478" />" width="80%">

<br><br>

**Admin Dashboard**
<br>
<img src="<img width="1917" height="1007" alt="admin-dashboard" src="https://github.com/user-attachments/assets/d32d41fb-3796-4932-844e-5dbce1d9b6d9" />" width="80%">

</div>

## 🤝 Contributing

We welcome contributions to this project. Please follow these steps to contribute:

1. **Fork the repository.**
2. **Create a new branch** (`git checkout -b feature/your-feature-name`).
3. **Make your changes** and commit them (`git commit -m 'Add some feature'`).
4. **Push to the branch** (`git push origin feature/your-feature-name`).
5. **Open a pull request**.

## 🐛 Issues

If you encounter any issues while using or setting up the project, please check the [Issues](https://github.com/Springboard-mentor-4721/Ecotrack-Group-2/issues) section to see if it has already been reported. If not, feel free to open a new issue detailing the problem.

When reporting an issue, please include:

- A clear and descriptive title.
- A detailed description of the problem.
- Steps to reproduce the issue.
- Any relevant logs or screenshots.
- The environment in which the issue occurs (OS, browser, Java/Node version, etc.).

## 📜 License

Distributed under the MIT License. See [License](/LICENSE) for more information.
