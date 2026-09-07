'use client';

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <html>
      <body>
        <div className="flex flex-col items-center justify-center w-screen h-screen bg-black text-white">
          <h2 className="text-2xl mb-4">Something went wrong!</h2>
          <button
            className="px-4 py-2 bg-red-600 rounded text-white"
            onClick={() => reset()}
          >
            Try again
          </button>
        </div>
      </body>
    </html>
  )
}
