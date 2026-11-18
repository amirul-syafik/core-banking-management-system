# CBMS Frontend

The CBMS frontend is a React and TypeScript single-page application built with Vite. It calls the Spring Boot API through the `/api` development proxy.

## Local development

Install the frontend dependencies, start the backend on port 8080, then run:

```sh
npm run dev
```

Vite serves the application at `http://localhost:5173`.

## Available scripts

- `npm run dev` starts the Vite development server.
- `npm run lint` runs ESLint.
- `npm run build` runs TypeScript project checks and creates the production bundle.
- `npm run preview` serves the production bundle locally.

See the [project README](../README.md) for setup details and the [screenshots](../docs/screenshots/) for UI references.
