import "./globals.css";
import { AppProviders } from "@/app/providers";
import { AppLayout } from "@/layout/AppLayout";

export default function RootLayout({
                                       children,
                                   }: {
    children: React.ReactNode;
}) {
    return (
        <html lang="ko">
        <body>
        <AppProviders>
            <AppLayout>
                {children}
            </AppLayout>
        </AppProviders>

        </body>
        </html>
    );
}