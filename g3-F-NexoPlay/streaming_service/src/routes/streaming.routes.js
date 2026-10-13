
import { Router } from "express";

import {
  catalog,
  videoDetail,
  availability,
  masterPlaylist,
  thumbnail,
  hlsResource
} from "../controllers/streaming.controller.js";

const router = Router();

router.get("/", catalog);

router.get("/:videoId", videoDetail);

router.get("/:videoId/availability", availability);

router.get("/:videoId/master.m3u8", masterPlaylist);

router.get("/:videoId/thumbnail", thumbnail);

router.get("/:videoId/hls/*resourcePath", hlsResource);

export default router;
