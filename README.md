# supermarket-receipts

## **Project Overview**

This project aims to help individuals or families better understand and manage their supermarket spending habits by scanning and analyzing paper receipts. The application allows users to upload receipts, parse the data, and gain insights into their spending trends.

### **Key Features**

- **QR Code Scanning**: Users can scan QR codes on paper receipts to extract receipt data via a URL pointing to a detailed webpage.
- **Receipt Parsing**: The application will parse receipts created by Entersoft and Epsilonnet, extracting structured data (store name, items, prices, etc.) for further analysis.
- **User Authentication**: Users can sign up or log in using Google, Apple, or email.
- **Analytics**: The app will provide analytics, tracking spending trends, item categories, and household needs over different timeframes (e.g., weekly, monthly).
- **Family Account**: Users can create family accounts, enabling multiple users (family members) to track receipts and share analytics.
- **Notifications**: The app will send notifications/reminders about incomplete receipts or suggested spending insights.

### More details
- [Specification](spec.md)
- [To-Do List](todo.md)

This is a [Next.js](https://nextjs.org) project bootstrapped with [`create-next-app`](https://nextjs.org/docs/app/api-reference/cli/create-next-app).

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
# or
bun dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

You can start editing the page by modifying `app/page.tsx`. The page auto-updates as you edit the file.

This project uses [`next/font`](https://nextjs.org/docs/app/building-your-application/optimizing/fonts) to automatically optimize and load [Geist](https://vercel.com/font), a new font family for Vercel.

## Learn More

To learn more about Next.js, take a look at the following resources:

- [Next.js Documentation](https://nextjs.org/docs) - learn about Next.js features and API.
- [Learn Next.js](https://nextjs.org/learn) - an interactive Next.js tutorial.

You can check out [the Next.js GitHub repository](https://github.com/vercel/next.js) - your feedback and contributions are welcome!

## Deploy on Vercel

The easiest way to deploy your Next.js app is to use the [Vercel Platform](https://vercel.com/new?utm_medium=default-template&filter=next.js&utm_source=create-next-app&utm_campaign=create-next-app-readme) from the creators of Next.js.

Check out our [Next.js deployment documentation](https://nextjs.org/docs/app/building-your-application/deploying) for more details.
