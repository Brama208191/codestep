// Bundle the Monaco editor with the app instead of letting @monaco-editor/react fetch it from a CDN at
// run time: no third-party script runs on the page, and the Content-Security-Policy stays at 'self'.
// Only the editor core and Java syntax highlighting are included.
import { loader } from "@monaco-editor/react";
import * as monaco from "monaco-editor/editor/editor.api";
import "monaco-editor/languages/definitions/java/register";
import EditorWorker from "monaco-editor/editor/editor.worker?worker";

self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
loader.config({ monaco });
