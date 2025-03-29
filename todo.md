# Supermarket Receipt Application - To-Do Checklist

## **1. User Authentication and Family Management**

### **1.1 Set Up Authentication with Supabase**

- [ ] Set up Supabase project.
- [ ] Install Supabase SDK in frontend and backend.
- [ ] Configure authentication methods (email/password, Google OAuth, Apple OAuth).

### **1.2 Email/Password Login**

- [ ] Implement email/password login page.
- [ ] Implement password reset functionality.
- [ ] Handle errors and edge cases for login (e.g., incorrect credentials).

### **1.3 Google OAuth Login**

- [ ] Set up OAuth with Google in Supabase.
- [ ] Implement Google login button in frontend.
- [ ] Handle token exchange and user session.

### **1.4 Apple OAuth Login**

- [ ] Set up OAuth with Apple in Supabase.
- [ ] Implement Apple login button in frontend.
- [ ] Handle token exchange and user session.

### **1.5 Family Management**

- [ ] Implement family account creation.
- [ ] Allow users to invite family members.
- [ ] Store family data in Supabase and link users to families.

## **2. Receipt Upload and QR Code Scanning**

### **2.1 Set Up QR Code Scanning**

- [ ] Install `react-qr-reader` library.
- [ ] Create a receipt upload page with QR code scanning functionality.
- [ ] Ensure proper handling of QR code data and receipt URL extraction.

### **2.2 Manual Receipt URL Entry**

- [ ] Implement functionality for users to manually enter a URL for receipts.
- [ ] Ensure URL validation (correct format, accessible URL).

### **2.3 Store Receipt Data**

- [ ] Store scanned or entered URLs in Supabase linked to the authenticated user.
- [ ] Ensure data consistency and validation.

## **3. Receipt Parsing and Data Extraction**

### **3.1 Fetch and Parse Receipt Data**

- [ ] Create a backend function to fetch receipt data from a URL.
- [ ] Implement logic to handle different receipt formats (Entersoft, Epsilonnet).
- [ ] Use `cheerio` or similar HTML scraping library to extract receipt information (store name, item names, prices, etc.).

### **3.2 Store Parsed Data in Supabase**

- [ ] Store parsed receipt data in a `Receipt` model in Supabase.
- [ ] Store individual item data in an `Item` model, linked to receipts.

### **3.3 Error Handling for Parsing**

- [ ] Implement error handling for invalid receipts or missing data.
- [ ] Log errors for failed parsing attempts.

## **4. Analytics and Spending Trends**

### **4.1 Aggregate Spending Data**

- [ ] Implement logic to aggregate total spending by family and by category (e.g., groceries, toiletries).
- [ ] Implement logic to calculate spending trends over time (weekly, monthly).

### **4.2 Display Analytics on Frontend**

- [ ] Create a frontend page to display spending trends and total spend.
- [ ] Display analytics by category.
- [ ] Display time-based spending reports (weekly, monthly).

### **4.3 Report Generation**

- [ ] Implement downloadable spending reports (CSV, PDF).
- [ ] Generate reports dynamically based on user preferences.

## **5. Notification System**

### **5.1 Set Up Notification System**

- [ ] Set up Vercel serverless functions for notifications (push, email).
- [ ] Integrate notification system with Supabase.

### **5.2 Incomplete Receipts Notifications**

- [ ] Implement logic to send notifications when a user has incomplete receipts.
- [ ] Send email/push notifications with reminders to upload missing receipts.

### **5.3 Spending Insights Notifications**

- [ ] Implement logic to send notifications about potential savings based on spending trends.
- [ ] Send notifications about overspending in specific categories.

### **5.4 Store Notification Status**

- [ ] Create a `Notifications` model to track the status of sent notifications (sent, failed).

### **5.5 Handle Errors in Notifications**

- [ ] Implement error handling for failed notifications (e.g., incorrect email, phone number).

## **6. Frontend Implementation**

### **6.1 Set Up Project Structure**

- [ ] Set up React project with Chakra UI.
- [ ] Set up Next.js for SSR/SSG.

### **6.2 Authentication Pages**

- [ ] Design and implement a registration/login page.
- [ ] Design and implement password recovery page.
- [ ] Design and implement family management page.

### **6.3 Receipt Management UI**

- [ ] Create a receipt dashboard page.
- [ ] Implement a receipt upload section with QR code scanning and URL entry.
- [ ] Display receipt data (store name, items, prices).

### **6.4 Analytics Pages**

- [ ] Create a page for displaying spending trends.
- [ ] Implement charts/graphs for spending analytics.
- [ ] Display categorized spending data.

### **6.5 User Profile and Family Settings**

- [ ] Create a page for managing user profile settings (name, email).
- [ ] Allow users to view and manage family members and settings.

### **6.6 Routing with Next.js**

- [ ] Set up routing for authentication pages, dashboard, and settings pages.

## **7. Backend Implementation**

### **7.1 Set Up Serverless Functions**

- [ ] Create Vercel serverless functions for user authentication and receipt processing.
- [ ] Create serverless functions for notification handling.

### **7.2 API Endpoints for Receipt Parsing**

- [ ] Set up API endpoints to process receipt URLs.
- [ ] Implement logic to fetch, parse, and store receipt data.

### **7.3 Database Schema for Receipts and Analytics**

- [ ] Set up Supabase database schemas for receipts, items, and analytics.
- [ ] Ensure relationships between models (user, family, receipt, item, analytics).

### **7.4 Integration with Frontend**

- [ ] Integrate backend APIs with frontend components.
- [ ] Ensure proper handling of user authentication in backend APIs.

### **7.5 Error Handling in Backend**

- [ ] Implement error handling for failed API requests.
- [ ] Log errors for failed database writes or receipt parsing.

## **8. Testing and Deployment**

### **8.1 Unit Testing**

- [ ] Write unit tests for frontend components (using Jest).
- [ ] Write unit tests for backend logic (using Jest or Mocha).

### **8.2 Integration Testing**

- [ ] Test communication between frontend and backend.
- [ ] Test API endpoints with Postman or Supertest.

### **8.3 End-to-End Testing**

- [ ] Use Cypress or Puppeteer to simulate user interaction.
- [ ] Test entire user journey (signup, receipt upload, analytics).

### **8.4 CI/CD Setup**

- [ ] Set up GitHub Actions for continuous integration.
- [ ] Automate deployment to Vercel after passing tests.

### **8.5 Final Deployment**

- [ ] Deploy frontend and backend to Vercel.
- [ ] Test the live application to ensure everything is working as expected.

## **9. Post-Deployment**

- [ ] Monitor app performance using tools like Sentry or Datadog.
- [ ] Implement user feedback and fix bugs as necessary.
- [ ] Collect user data and metrics to improve features.
